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

  def frameCapture(videoUrl: URL, elapsedTime: Duration, target: Path): Either[Throwable, Path] =
    val time = DurationUtil.toHMS(elapsedTime)
    val cmd  = s"ffmpeg -ss $time -i $videoUrl -frames.v 1 -q:v 1 $target"
    Try(cmd.!!) match
      case Success(_) => Right(target)
      case Failure(e) => Left(e)
