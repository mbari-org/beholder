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

package org.mbari.beholder.etc.ffmpeg

import java.net.URL
import java.nio.file.Path
import java.time.Duration
import org.mbari.beholder.etc.jdk.DurationUtil
import org.mbari.beholder.etc.jdk.Logging.given
import scala.util.{Failure, Success, Try}
import sys.process.*

/**
 * Utility functions for using ffmpeg. MOre docs at https://trac.ffmpeg.org/wiki/Seeking
 */
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
   * @param accurate
   *   By default ffmpeg will return "frame accutrate" capture. If you want the nearest preceding
   *   keyframe, use false
   */
  def frameCapture(
      videoUrl: URL,
      elapsedTime: Duration,
      target: Path,
      accurate: Boolean = true
  ): Either[Throwable, Path] =
    val time = DurationUtil.toHMS(elapsedTime)
    /*
     -ss Seek.        This needs to be first. If it's after -i the capture is MUCH slower
     -i               Input file or URL
     -frames:v 1      Frame quality 1 (best) to 5
     -q:v 1           ?
     -hide_banner     Make quiet
     -loglevel error  Make quieter
     */
    val nas  = if !accurate then "-noaccurate_seek" else ""
    val cmd  =
      s"ffmpeg -ss $time ${nas} -i $videoUrl -frames:v 1 -qmin 1 -q:v 1 -hide_banner -loglevel error -y $target"
    log.atWarn.log(() => s"Executing $cmd")
    Try(cmd.!!) match
      case Success(_) => Right(target)
      case Failure(e) => Left(e)
