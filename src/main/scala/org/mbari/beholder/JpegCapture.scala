/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder

import java.net.URL
import java.time.Duration
import org.mbari.beholder.etc.ffmpeg.FfmpegUtil
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.etc.jdk.DurationUtil
import java.nio.file.Files
import org.mbari.beholder.etc.jdk.PathUtil

class JpegCapture(cache: JpegCache):

  private val log = System.getLogger(getClass.getName)

  def capture(videoUrl: URL, elapsedTime: Duration): Option[Jpeg] =
    cache.get(videoUrl, elapsedTime) match
      case Some(jpeg) => Some(jpeg)
      case None       => grabFrame(videoUrl, elapsedTime)

  private def grabFrame(videoUrl: URL, elapsedTime: Duration): Option[Jpeg] =
    val jpeg = Jpeg.toPath(cache.root, videoUrl, elapsedTime)
    if (PathUtil.isChild(cache.root, jpeg.path))
      val parent = jpeg.path.getParent()
      if (!Files.exists(parent))
        Files.createDirectories(parent)
    FfmpegUtil.frameCapture(videoUrl, elapsedTime, jpeg.path) match
      case Left(e)     =>
        log
          .withCause(e)
          .atDebug
          .log(() =>
            s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUrl"
          )
        None
      case Right(path) =>
        val sizeBytes = Files.size(path)
        val theJpeg   = jpeg.copy(path = path, sizeBytes = Some(sizeBytes))
        cache.put(theJpeg)
        Some(theJpeg)
