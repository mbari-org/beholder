/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.etc.ffmpeg

import java.time.Duration
import java.nio.file.Path
import java.net.URL
import org.mbari.beholder.etc.jdk.DurationUtil
import sys.process._
import scala.util.{Failure, Success, Try}

object FfmpegUtil:
  private val log = System.getLogger(getClass.getName())

  /**
   * Capture a frame from a video at a given time and save it to a file.
   * @param videoUrl
   *   The video to fetch from
   * @param elapsedTime
   *   The time into the video to grab a frame
   * @param target
   *   The location to save the image to
   */
  def frameCapture(videoUrl: URL, elapsedTime: Duration, target: Path): Either[Throwable, Path] =
    val time = DurationUtil.toHMS(elapsedTime)
    /*
     -ss Seek.        This needs to be first. If it's after -i the capture is MUCH slower
     -i               Input file or URL
     -frames:v 1      Frame quality 1 (best) to 5
     -q:v 1           ?
     -hide_banner     Make quiet
     -loglevel error  Make quieter
     */
    val cmd  =
      s"ffmpeg -ss $time -i $videoUrl -frames:v 1 -q:v 1 -hide_banner -loglevel error -y $target"
    Try(cmd.!!) match
      case Success(_) => Right(target)
      case Failure(e) => Left(e)
