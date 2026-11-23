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
import java.util.concurrent.{ConcurrentHashMap, ConcurrentSkipListSet, Executor, Executors}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.util.NumberUtil

import scala.jdk.CollectionConverters.*

/**
 * Disk-backed image frame cache. Frames are indexed by (videoUri, elapsedTime) for O(1) lookups and evicted
 * oldest-first when total on-disk size exceeds `maxCacheSizeMB`.
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
 * @param deleter
 *   Where evicted files are actually unlinked. An eviction pass can mean tens of thousands of deletes, and `freeDisk`
 *   runs on whichever thread called `put` -- a capture worker -- so doing them inline took a capture slot out of
 *   service for as long as the pass lasted. Injectable so tests can choose exactly when the deletes run.
 */
class ImageCacheImpl(
    val root: Path,
    maxCacheSizeMB: Double,
    cacheClearPct: Double = 0.20,
    deleter: Executor = ImageCacheImpl.defaultDeleter
) extends ImageCache:

    require(Files.isDirectory(root), "root must be a directory")
    require(Files.isWritable(root), "root must be writable")
    require(
        cacheClearPct > 0 && cacheClearPct <= 1,
        s"cacheClearPct must be between 0 and 1. You used $cacheClearPct"
    )

    private val log = System.getLogger(getClass.getName)

    // Two-level lookup: URI → (CachedImage.cacheKey → CachedImage). O(1) average per level.
    // The inner key is built in exactly one place, CachedImage.cacheKey, so that lookup, store,
    // removal and eviction cannot drift apart.
    // videoUri -> ((elapsedMs, imageType, deinterlace) -> CachedImage)
    private val index: ConcurrentHashMap[URI, ConcurrentHashMap[(Long, ImageType, Boolean), CachedImage]] =
        new ConcurrentHashMap()

    // Eviction queue ordered oldest-first by (created, videoUri, elapsedMs, imageType, deinterlace).
    // ConcurrentSkipListSet.pollFirst() atomically removes and returns the head element.
    private[beholder] val evictionOrdering: java.util.Comparator[CachedImage] = (a: CachedImage, b: CachedImage) =>
        val byTime = a.created.compareTo(b.created)
        if byTime != 0 then byTime
        else
            val byUri = a.videoUri.toString.compareTo(b.videoUri.toString)
            if byUri != 0 then byUri
            else
                val byElapsedTIme = java.lang.Long.compare(a.elapsedTime.toMillis, b.elapsedTime.toMillis)
                if byElapsedTIme != 0 then byElapsedTIme
                else
                    val byImageType = a.imageType.evictionOrder.compareTo(b.imageType.evictionOrder)
                    if byImageType != 0 then byImageType
                    else a.deinterlace.compareTo(b.deinterlace)

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
            .flatMap(m => Option(m.get(jpeg.cacheKey)))

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

        // If this (uri, elapsedTime, imageType, deinterlace) already exists, withdraw the old entry's accounting.
        val old = timeMap.put(stamped.cacheKey, stamped)
        if old != null then
            evictionQueue.remove(old)
            totalBytes.addAndGet(-old.sizeBytes.getOrElse(0L))

        evictionQueue.add(stamped)
        val newTotal = totalBytes.addAndGet(stamped.sizeBytes.getOrElse(0L))
        if newTotal > maxBytes then freeDisk()
        stamped

    /**
     * Mark a cache hit as just used, so eviction treats it as the newest entry. This is what makes eviction
     * least-recently-*used* rather than oldest-*captured*: without it a frame in constant demand was evicted as soon as
     * it was among the oldest captures, then immediately recaptured -- during the eviction pass, which is exactly when
     * a recapture is most exposed to that pass's pending deletes.
     *
     * Conditional on `cachedImage` still being the indexed entry. If eviction has already taken it, or a recapture has
     * replaced it, there is nothing to refresh, and an unconditional `put` would resurrect an entry whose file may be
     * about to be deleted.
     *
     * The index is swapped first and the eviction queue second. An eviction pass that polls the old entry in between
     * finds the index no longer holds it and skips it (see [[freeDisk]]), so the two cannot end up disagreeing.
     *
     * `created` is refreshed in memory only. After a restart `scanCache` restores it from the file's creation time, so
     * recency is lost across restarts; persisting it would cost a file write per hit.
     */
    def touch(cachedImage: CachedImage): Option[CachedImage] =
        Option(index.get(cachedImage.videoUri)).flatMap { timeMap =>
            val touched = cachedImage.copy(created = Instant.now())
            if timeMap.replace(cachedImage.cacheKey, cachedImage, touched) then
                evictionQueue.remove(cachedImage)
                evictionQueue.add(touched)
                Some(touched)
            else None
        }

    def remove(cachedImage: CachedImage): Option[CachedImage] =
        Option(index.get(cachedImage.videoUri)).flatMap { timeMap =>
            Option(timeMap.remove(cachedImage.cacheKey)).map { img =>
                withdraw(img)
                img
            }
        }

    def removeIfCurrent(cachedImage: CachedImage): Boolean =
        Option(index.get(cachedImage.videoUri)).exists { timeMap =>
            // Two-argument remove: only succeeds if the mapping is still this exact entry, so a fresher
            // capture that replaced it in the meantime is left alone.
            val removed = timeMap.remove(cachedImage.cacheKey, cachedImage)
            if removed then withdraw(cachedImage)
            removed
        }

    private def withdraw(img: CachedImage): Unit =
        evictionQueue.remove(img)
        totalBytes.addAndGet(-img.sizeBytes.getOrElse(0L))

    /**
     * Evict oldest entries until `currentSize * cacheClearPct` bytes have been freed, then hand the corresponding files
     * to `deleter`. pollFirst() is atomic — no separate lock needed for the dequeue step.
     *
     * The index is updated here, synchronously, so an evicted frame stops being served immediately; only the unlinking
     * is deferred. That leaves a gap between "gone from the index" and "gone from disk" in which the same frame can be
     * recaptured to the same path -- [[deleteFromDisk]] is what keeps the deferred delete from destroying that new
     * file.
     */
    private def freeDisk(): Unit =
        if evicting.compareAndSet(false, true) then
            try
                val targetFreeBytes = (totalBytes.get() * cacheClearPct).toLong
                var freedBytes      = 0L
                val toDelete        = collection.mutable.ArrayBuffer.empty[CachedImage]

                var cachedImage = Option(evictionQueue.pollFirst())
                while cachedImage.isDefined && freedBytes < targetFreeBytes do
                    val j            = cachedImage.get
                    // Conditional: `touch` or `put` may have replaced this entry since it was queued. The
                    // replacement owns the file, and `put` already withdrew the old entry's bytes, so a stale
                    // queue element is skipped rather than evicted -- removing by key alone would evict the
                    // fresh entry from the index while leaving it in the queue.
                    val stillIndexed = Option(index.get(j.videoUri)).exists(_.remove(j.cacheKey, j))
                    if stillIndexed then
                        freedBytes += j.sizeBytes.getOrElse(0L)
                        toDelete += j
                    cachedImage =
                        if freedBytes < targetFreeBytes then Option(evictionQueue.pollFirst())
                        else None

                totalBytes.addAndGet(-freedBytes)
                log.atDebug
                    .log(() => s"Evicted ${toDelete.size} jpegs (${NumberUtil.byteToMB(freedBytes)} MB) from cache")

                if toDelete.nonEmpty then
                    val batch = toDelete.toVector
                    deleter.execute(() => batch.foreach(deleteFromDisk))
            finally evicting.set(false)

    /**
     * Delete an evicted frame's file -- unless the frame has been cached again since it was evicted.
     *
     * Cache paths are derived from the request, not made unique, so a frame recaptured after its eviction is written to
     * the very path this is about to delete. Deleting by path alone destroyed that fresh file while the index went on
     * advertising it, and every later hit for the frame was a 500 (`FileNotFoundException` from Vert.x `sendFile`)
     * until a restart rebuilt the index.
     *
     * The check and the delete run inside `compute` on the frame's key, which holds the same bin lock `put` takes for
     * that key, so a `put` cannot slip in between them. What this cannot see is a capture whose ffmpeg has written the
     * file but which has not reached `put` yet; `ImageCapture` checks that a hit's file exists for that reason.
     */
    private[beholder] def deleteFromDisk(evicted: CachedImage): Unit =
        def unlink(): Unit =
            try Files.deleteIfExists(evicted.path)
            catch
                case e: Exception =>
                    log.atWarn.log(() => s"Failed to delete cached file ${evicted.path}: ${e.getMessage}")

        Option(index.get(evicted.videoUri)) match
            case None          => unlink()
            case Some(timeMap) =>
                timeMap.compute(
                    evicted.cacheKey,
                    (_, current) =>
                        if current == null then unlink()
                        else log.atDebug.log(() => s"Not deleting ${evicted.path}: it was recaptured after eviction")
                        current
                )

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
                    timeMap.put(timed.cacheKey, timed)
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

object ImageCacheImpl:

    /**
     * One daemon thread for every cache's deletes. One is enough -- deletes are cheap individually, and running them in
     * order keeps the disk from being hammered in parallel -- and daemon so it never holds the JVM open.
     */
    lazy val defaultDeleter: Executor =
        Executors.newSingleThreadExecutor: runnable =>
            val t = Thread(runnable, "beholder-cache-evict")
            t.setDaemon(true)
            t
