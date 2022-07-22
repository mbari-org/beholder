/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder

import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Path
import java.net.URL
import org.mbari.beholder.etc.ffmpeg.DurationString.*
import java.nio.file.Files
import scala.concurrent.duration.Duration.apply
import java.time.Duration
import org.mbari.beholder.etc.jdk.PathUtil
import org.mbari.beholder.etc.jdk.DurationUtil
import java.util.EnumSet
import scala.collection.Searching.Found
import java.nio.file.Paths
import scala.util.chaining.*
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant
import scala.jdk.CollectionConverters.*

/**
 * Information about the source of a JPEG
 * @param videoUrl
 *   The URL to the source video
 * @param duration
 *   The elapsed time into the video that the jpeg was taken
 * @param path
 *   The local path to the jpeg file
 */
case class Jpeg(
  videoUrl: URL, 
  duration: Duration, 
  path: Path, 
  created: Instant = Instant.now(),
  sizeBytes: Option[Long] = None
)

object Jpeg:

  /**
   * Generates jpeg info
   * @param root
   *   The root directory of the cache
   * @param url
   *   The video url
   * @param duration
   *   The elapsed tie into the video
   * @return
   *   The jpeg info
   */
  def toPath(root: Path, url: URL, duration: Duration): Jpeg =
    val filename = DurationUtil.toHMS(duration).replace(":", "_") + ".jpg"
    val parent   = PathUtil.toPath(root, url)
    val path     = parent.resolve(filename)
    Jpeg(url, duration, path)

  /**
   * Generates Jpeg info
   * @param root
   *   The root directory of the cache
   * @param file
   *   the jpeg file (must be under the root fo the cache directory)
   * @return
   *   A jpeg info. None the file is not a jpeg or if it's not under the cache directory
   */
  def fromPath(root: Path, file: Path): Option[Jpeg] =
    if (!Files.isDirectory(file) && PathUtil.isChild(root, file) && PathUtil.isJpeg(file))
      val parent = file.getParent
      PathUtil
        .fromPath(root, parent)
        .map(videoUrl =>
          val filename = PathUtil.dropExtension(file).replace("_", ":")
          val duration = DurationUtil.fromHMS(filename)
          val size = if (Files.isRegularFile(file)) Some(Files.size(file)) else None
          Jpeg(videoUrl, duration, file, sizeBytes = size)
        )
    else None

  private val fakeUrl = new URL("http://www.mbari.org")
  private val fakePath = Paths.get("/foo/bar.jpg")

  def fake(duration: Duration): Jpeg = Jpeg(fakeUrl, duration, fakePath)

class JpegCache(root: Path, maxSizeMB: Long, cacheClearSize: Int = 100):

  require(Files.isDirectory(root), "root must be a directory")
  require(Files.isWritable(root), "root must be writable")

  private given jpegOrdering: Ordering[Jpeg] = Ordering.by(_.duration)

  private val cache: ConcurrentHashMap[URL, List[Jpeg]] =
    new ConcurrentHashMap()

  private val cacheSizeMB: AtomicLong = AtomicLong()

  def get(jpeg: Jpeg): Option[Jpeg] = get(jpeg.videoUrl, jpeg.duration)

  def get(url: URL, duration: DurationString): Option[Jpeg] =
    get(url, duration)

  def get(url: URL, duration: Duration): Option[Jpeg] =
    Option(cache.get(url)) match 
      case None => None
      case Some(jpegs) => 
        jpegs.search(Jpeg.fake(duration))(jpegOrdering) match 
          case Found(i) => Some(jpegs(i))
          case _ => None

  def put(jpeg: Jpeg): Jpeg = synchronized {
    val newList = (jpeg :: cache.getOrDefault(jpeg.videoUrl, Nil)).sortBy(_.duration)
    cache.put(jpeg.videoUrl, newList)
    jpeg.sizeBytes.foreach(s => {
      val newCacheSizeMB = cacheSizeMB.addAndGet(s)

    })
    jpeg
  }



  def freeDisk(currentSizeMB: Long): Unit = 
    if (currentSizeMB > maxSizeMB)
      synchronized {

        cache
          .asScala
          .values
          .flatten
          .flatMap(_.sizeBytes)
          .map(_ / 1048576)
      }

  def put(url: URL, duration: Duration, path: Path): Option[Jpeg] = 
    Jpeg.fromPath(root, path)
      .map(jpeg => {
        put(jpeg)
        jpeg
      })

  def put(url: URL, duration: DurationString, path: Path): Option[Jpeg] =
    put(url, duration, path)



  def rescan(): Unit = synchronized {
    cache.clear()
    val visitor = new java.nio.file.SimpleFileVisitor[Path]:
      override def visitFile(
          file: Path,
          attrs: java.nio.file.attribute.BasicFileAttributes
      ): java.nio.file.FileVisitResult =
        Jpeg.fromPath(root, file).foreach(j => put(j.videoUrl, j.duration, j.path))
        java.nio.file.FileVisitResult.CONTINUE
    Files.walkFileTree(root, visitor)
  }
