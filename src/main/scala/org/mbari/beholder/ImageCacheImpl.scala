/*
 * Copyright 2022 MBARI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mbari.beholder

import java.net.URI
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.util.NumberUtil

import scala.jdk.CollectionConverters.*

/**
 * Disk-backed image frame cache. Frames are indexed by (videoUri, elapsedTime) for O(1) lookups and evicted oldest-first
 * when total on-disk size exceeds `maxCacheSizeMB`.
 *
 * Improvements over JpegCache:
 *   - O(1) lookup via nested ConcurrentHashMap (URI → elapsedMs → Jpeg) instead of O(n) TreeSet.find
 *   - O(log n) eviction via ConcurrentSkipListSet ordered by creation time; pollFirst() is atomic
 *   - AtomicLong byte counter avoids floating-point drift from summing MB values
 *   - AtomicBoolean eviction guard prevents concurrent eviction storms without a global lock
 *   - scanCache preserves real file creation timestamps for correct age-ordering on restart
 *
 * @param root
 *   Root directory for cached JPEG files
 * @param maxCacheSizeMB
 *   Maximum allowed on-disk cache size in MB
 * @param cacheClearPct
 *   Fraction of current cache size to free when the limit is exceeded (0 < x <= 1)
 */
class ImageCacheImpl(val root: Path, maxCacheSizeMB: Double, cacheClearPct: Double = 0.20) extends ImageCache:

    require(Files.isDirectory(root), "root must be a directory")
    require(Files.isWritable(root), "root must be writable")
    require(
        cacheClearPct > 0 && cacheClearPct <= 1,
        s"cacheClearPct must be between 0 and 1. You used $cacheClearPct"
    )

    private val log = System.getLogger(getClass.getName)

    // Two-level lookup: URI → ((elapsedTimeMillis, ImageType) → CachedImage). O(1) average per level.
    private val index: ConcurrentHashMap[URI, ConcurrentHashMap[(Long, ImageType), CachedImage]] =
        new ConcurrentHashMap()

    // Eviction queue ordered oldest-first by (created, videoUri, elapsedMs, imageType).
    // ConcurrentSkipListSet.pollFirst() atomically removes and returns the head element.
    private val evictionOrdering: java.util.Comparator[CachedImage] = (a: CachedImage, b: CachedImage) =>
        val byTime = a.created.compareTo(b.created)
        if byTime != 0 then byTime
        else
            val byUri = a.videoUri.toString.compareTo(b.videoUri.toString)
            if byUri != 0 then byUri
            else
                val byElapsedTIme = java.lang.Long.compare(a.elapsedTime.toMillis, b.elapsedTime.toMillis)
                if byElapsedTIme != 0 then byElapsedTIme
                else a.imageType.evictionOrder.compareTo(b.imageType.evictionOrder)

    private val evictionQueue: ConcurrentSkipListSet[CachedImage] =
        new ConcurrentSkipListSet[CachedImage](evictionOrdering)

    // Track total cache size in bytes (integer arithmetic — no floating-point drift).
    private val totalBytes: AtomicLong = new AtomicLong(0L)
    private val maxBytes: Long         = NumberUtil.mbToByte(maxCacheSizeMB)

    // Guard: only one eviction pass at a time. A second concurrent caller simply returns.
    private val evicting: AtomicBoolean = new AtomicBoolean(false)

    scanCache()

    def currentCacheSizeMB: Double = NumberUtil.byteToMB(totalBytes.get())

    def get(jpeg: CachedImage): Option[CachedImage] =
        Option(index.get(jpeg.videoUri))
            .flatMap(m => Option(m.get((jpeg.elapsedTime.toMillis, jpeg.imageType))))

    /**
     * Store a image in the cache. Stamps the creation time as now, then triggers eviction if total size exceeds the
     * limit.
     *
     * @return
     *   The stored Jpeg (with updated creation time)
     */
    def put(cachedImage: CachedImage): CachedImage =
        val stamped = cachedImage.copy(created = Instant.now())
        val timeMap = index.computeIfAbsent(stamped.videoUri, _ => new ConcurrentHashMap())

        // If this (uri, elapsedTime, imageType) already exists, withdraw the old entry's accounting.
        val old = timeMap.put((stamped.elapsedTime.toMillis, stamped.imageType), stamped)
        if old != null then
            evictionQueue.remove(old)
            totalBytes.addAndGet(-old.sizeBytes.getOrElse(0L))

        evictionQueue.add(stamped)
        val newTotal = totalBytes.addAndGet(stamped.sizeBytes.getOrElse(0L))
        if newTotal > maxBytes then freeDisk()
        stamped

    def remove(cachedImage: CachedImage): Option[CachedImage] =
        Option(index.get(cachedImage.videoUri)).flatMap { timeMap =>
            Option(timeMap.remove((cachedImage.elapsedTime.toMillis, cachedImage.imageType))).map { img =>
                evictionQueue.remove(img)
                totalBytes.addAndGet(-img.sizeBytes.getOrElse(0L))
                img
            }
        }

    /**
     * Evict oldest entries until `currentSize * cacheClearPct` bytes have been freed, then delete the corresponding
     * files from disk. pollFirst() is atomic — no separate lock needed for the dequeue step.
     */
    private def freeDisk(): Unit =
        if evicting.compareAndSet(false, true) then
            try
                val targetFreeBytes = (totalBytes.get() * cacheClearPct).toLong
                var freedBytes      = 0L
                val toDelete        = collection.mutable.ArrayBuffer.empty[CachedImage]

                var cachedImage = Option(evictionQueue.pollFirst())
                while cachedImage.isDefined && freedBytes < targetFreeBytes do
                    val j = cachedImage.get
                    Option(index.get(j.videoUri)).foreach(_.remove((j.elapsedTime.toMillis, j.imageType)))
                    freedBytes += j.sizeBytes.getOrElse(0L)
                    toDelete += j
                    cachedImage =
                        if freedBytes < targetFreeBytes then Option(evictionQueue.pollFirst())
                        else None

                totalBytes.addAndGet(-freedBytes)
                log.atDebug
                    .log(() => s"Evicted ${toDelete.size} jpegs (${NumberUtil.byteToMB(freedBytes)} MB) from cache")

                // Delete files outside the eviction loop — no lock contention here.
                toDelete.foreach(deleteFromDisk)
            finally evicting.set(false)

    private def deleteFromDisk(jpeg: CachedImage): Unit =
        if Files.exists(jpeg.path) then
            try Files.delete(jpeg.path)
            catch
                case e: Exception =>
                    log.atWarn.log(() => s"Failed to delete cached file ${jpeg.path}: ${e.getMessage}")

    /**
     * Scan the cache root directory and rebuild the in-memory index from existing files. Uses each file's filesystem
     * creation timestamp so eviction ordering reflects true file age after a restart (unlike put(), which stamps
     * Instant.now()). Does NOT trigger eviction during the scan.
     */
    def scanCache(): Unit =
        index.clear()
        evictionQueue.clear()
        totalBytes.set(0L)

        val visitor = new java.nio.file.SimpleFileVisitor[Path]:
            override def visitFile(
                file: Path,
                attrs: java.nio.file.attribute.BasicFileAttributes
            ): java.nio.file.FileVisitResult =
                CachedImage.fromPath(root, file).foreach { jpeg =>
                    val timed   = jpeg.copy(created = attrs.creationTime().toInstant)
                    val timeMap = index.computeIfAbsent(timed.videoUri, _ => new ConcurrentHashMap())
                    timeMap.put((timed.elapsedTime.toMillis, timed.imageType), timed)
                    evictionQueue.add(timed)
                    totalBytes.addAndGet(timed.sizeBytes.getOrElse(0L))
                }
                java.nio.file.FileVisitResult.CONTINUE

        Files.walkFileTree(root, visitor)

    /**
     * Remove all cached JPEGs from disk and clear the in-memory index.
     */
    def clearCache(): Unit =
        val snapshot = evictionQueue.iterator().asScala.toList
        evictionQueue.clear()
        index.clear()
        totalBytes.set(0L)
        snapshot.foreach { jpeg =>
            if Files.exists(jpeg.path) && Files.isWritable(jpeg.path) then
                try Files.delete(jpeg.path)
                catch
                    case e: Exception =>
                        log.atWarn.log(() => s"Failed to delete ${jpeg.path}: ${e.getMessage}")
        }

    def totalImages: Int = index.values().asScala.map(_.size()).sum
