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

/**
 * Information about the source of a JPEG
 * @param videoUrl
 *   The URL to the source video
 * @param duration
 *   The elapsed time into the video that the jpeg was taken
 * @param path
 *   The local path to the jpeg file
 */
case class Jpeg(videoUrl: URL, duration: Duration, path: Path)

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
          Jpeg(videoUrl, duration, file)
        )
    else None

class JpegCache(root: Path):

  require(Files.isDirectory(root), "root must be a directory")
  require(Files.isWritable(root), "root must be writable")

  val cache: ConcurrentHashMap[URL, ConcurrentHashMap[DurationString, Path]] =
    new ConcurrentHashMap()

  def get(url: URL, duration: DurationString): Option[Path] =
    Option(cache.get(url)) match
      case Some(urlCache) => Option(urlCache.get(duration))
      case None           => None

  def get(url: URL, duration: Duration): Option[Path] =
    get(url, DurationString(duration))

  def put(url: URL, duration: DurationString, path: Path): Either[Throwable, Path] = synchronized {
    if (PathUtil.isJpeg(path))
      Option(cache.get(url)) match
        case Some(urlCache) => Right(urlCache.put(duration, path))
        case None           =>
          val urlCache = new ConcurrentHashMap[DurationString, Path]
          urlCache.put(duration, path)
          cache.put(url, urlCache)
          Right(path)
    else Left(new IllegalArgumentException(s"$path is not a jpeg"))
  }

  def put(url: URL, duration: Duration, path: Path): Either[Throwable, Path] =
    put(url, DurationString(duration), path)

  def rescan(): Unit = synchronized {
    cache.clear()
    val visitor = new java.nio.file.SimpleFileVisitor[Path]:
      override def visitFile(
          file: Path,
          attrs: java.nio.file.attribute.BasicFileAttributes
      ): java.nio.file.FileVisitResult =
        if (!Files.isDirectory(file) && PathUtil.isJpeg(file))
          Jpeg
            .fromPath(root, file)
            .foreach(jpeg => put(jpeg.videoUrl, jpeg.duration, jpeg.path))
          java.nio.file.FileVisitResult.CONTINUE
        else java.nio.file.FileVisitResult.CONTINUE
    Files.walkFileTree(root, visitor)
  }
