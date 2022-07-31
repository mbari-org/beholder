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

import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import java.util.EnumSet
import org.mbari.beholder.etc.ffmpeg.DurationString.*
import org.mbari.beholder.etc.jdk.{DurationUtil, PathUtil}
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.util.{ListUtil, NumberUtil}
import scala.collection.Searching.Found
import scala.concurrent.duration.Duration.apply
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

/**
 * In memory cache of paths to Jpegs that were captured. The cache is organized by videoUrl and
 * elpased time into the video. When the cache on disk size exceeds the allowed size, the oldest
 * jpegs are cleared out of the cache and removed from disk. The number of jpegs removed is
 * specified by the cacheClearPct.
 *
 * Jpegs are expected to be stored in jpegs named as follows: <root>/<video url host>/<video url
 * path>/<elapsed time as hh_mm_ss.sss.jpg>
 *
 * @param root
 *   The root directory of the cache
 * @param maxCacheSizeMB
 *   The max allowed on-disk size of the cache
 * @param cacheClearPct
 *   When the maxCacheSizeMB is receached this, disk will be freed equal to maxCacheSizeMb *
 *   cacheClearPct
 */
class JpegCache(val root: Path, maxCacheSizeMB: Double, cacheClearPct: Double = 0.20):

  require(Files.isDirectory(root), "root must be a directory")
  require(Files.isWritable(root), "root must be writable")
  require(
    cacheClearPct > 0 && cacheClearPct <= 1,
    s"cacheClearPct must be between 0 and 1. You used $cacheClearPct"
  )

  private val log = System.getLogger(getClass.getName)

  private given jpegOrdering: Ordering[Jpeg] = Ordering.by(_.elapsedTime)

  /**
   * A synchronized map. The URL is the video URL. The list is a sorted (by elapsedTime/elapsed time
   * into the video) immutable list.
   */
  private val cache: ConcurrentHashMap[URL, List[Jpeg]] =
    new ConcurrentHashMap()

  /** Tracks current cache size */
  private val cacheSizeMB: AtomicReference[Double] = AtomicReference()

  // -- READ EXISTING FILES INTO CACHE. NEEDED IF SERVER CRASHES TO REBUILT EXISTING CACHE
  scanCache()

  def currentCacheSizeMB: Double = cacheSizeMB.get()

  /**
   * @param jpeg
   *   The jpeg of interest. This only needs valid url and elapsedTime fields
   * @return
   *   The jpeg in the cache
   */
  def get(jpeg: Jpeg): Option[Jpeg] =
    Option(cache.get(jpeg.videoUrl)) match
      case None        => None
      case Some(jpegs) =>
        jpegs.search(jpeg)(jpegOrdering) match
          case Found(i) => Some(jpegs(i))
          case _        => None

  def get(url: URL, elapsedTime: DurationString): Option[Jpeg] =
    get(url, elapsedTime)

  /**
   * @param url
   *   The video URL
   * @param elapsedTime
   *   The elapsed time into the video
   */
  def get(url: URL, elapsedTime: Duration): Option[Jpeg] =
    get(Jpeg.fake(url, elapsedTime))

  /**
   * Store a jpeg in the cache
   * @param jpeg
   *   The jpeg to store
   * @return
   *   The stored jpeg
   */
  def put(jpeg: Jpeg): Jpeg = synchronized {
    val newList = (jpeg :: cache.getOrDefault(jpeg.videoUrl, Nil)).sortBy(_.elapsedTime)
    cache.put(jpeg.videoUrl, newList)
    // -- Cache size is updated and/or freed here

    jpeg.sizeMB.foreach(s => freeDisk(cacheSizeMB.accumulateAndGet(s, (a, b) => a + b)))
    jpeg
  }

  /**
   * Store a jpeg in the cache
   * @param url
   *   The video URL
   * @param elapsedTime
   *   The elasped time into the video
   * @param path
   *   The on disk location of the jpeg. It must be under the cache's root directory
   */
  def put(url: URL, elapsedTime: Duration, path: Path): Option[Jpeg] =
    Jpeg.fromPath(root, path).map(put)

  def put(url: URL, elapsedTime: DurationString, path: Path): Option[Jpeg] =
    put(url, elapsedTime, path)

  def remove(url: URL, elapsedTime: Duration): Option[Jpeg] =
    remove(Jpeg.fake(url, elapsedTime))

  /**
   * Remove a jpeg from the cache.
   * @param jpeg
   *   the jpeg to remove
   */
  def remove(jpeg: Jpeg): Option[Jpeg] = synchronized {
    Option(cache.get(jpeg.videoUrl)) match
      case None        => None
      case Some(jpegs) =>
        jpegs.search(jpeg)(jpegOrdering) match
          case Found(i) =>
            val theJpeg = jpegs(i)
            val newList = ListUtil.removeAtIdx(i, jpegs)
            cache.put(jpeg.videoUrl, newList)
            // -- The cache size is updated here
            cacheSizeMB.getAndUpdate(i => i - theJpeg.sizeMB.getOrElse(0d))
            Some(theJpeg)
          case _        => None
  }

  /**
   * Checks the cache size and frees up disk if needed
   * @param currentSizeMB
   *   the current disk size
   */
  private def freeDisk(currentSizeMB: Double): Unit =
    if (currentSizeMB >= maxCacheSizeMB)

      val dropSize = currentSizeMB * cacheClearPct

      synchronized {
        var jpegs = cache
          .asScala
          .values
          .flatten
          .toSeq
          .sortBy(_.created)

        val numToDrop = jpegs
          .to(LazyList)
          .map(_.sizeMB.getOrElse(0d))
          .scanLeft(0d)(_ + _)
          .takeWhile(s => s < dropSize)
          .size

        log
          .atDebug
          .log(() => s"Freeing $numToDrop jpegs from the cache")

        jpegs
          .take(numToDrop)
          .foreach(dropJpeg)
      }

  /** helper function tor remove a jpeg from cache and disk */
  private def dropJpeg(jpeg: Jpeg): Unit =
    remove(jpeg.videoUrl, jpeg elapsedTime)
    if (Files.exists(jpeg.path))
      Files.delete(jpeg.path)

  /** On start we check the disk and load any jpegs already in the cache */
  private def scanCache(): Unit = synchronized {
    cache.clear()
    val visitor = new java.nio.file.SimpleFileVisitor[Path]:
      override def visitFile(
          file: Path,
          attrs: java.nio.file.attribute.BasicFileAttributes
      ): java.nio.file.FileVisitResult =
        Jpeg.fromPath(root, file).foreach(j => put(j))
        java.nio.file.FileVisitResult.CONTINUE
    Files.walkFileTree(root, visitor)
  }

  /**
   * Removes all jpegs in the cache from disk
   */
  def clearCache(): Unit = synchronized {
    cache
      .asScala
      .values
      .flatten
      .foreach(jpeg =>
        if (Files.exists(jpeg.path))
          if (Files.isWritable(jpeg.path))
            Files.delete(jpeg.path)
          else
            log.atWarn.log(() => s"Unable to delete ${jpeg.path} from cache root directory")
      )
    cache.clear()
  }
