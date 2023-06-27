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
import java.time.Duration
import org.mbari.beholder.etc.ffmpeg.FfmpegUtil
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.etc.jdk.DurationUtil
import java.nio.file.Files
import org.mbari.beholder.etc.jdk.PathUtil
import org.mbari.beholder.api.ErrorMsg
import org.mbari.beholder.api.StatusMsg
import org.mbari.beholder.api.ServerError

class JpegCapture(cache: JpegCache):

  private val log = System.getLogger(getClass.getName)

  def capture(
      videoUrl: URL,
      elapsedTime: Duration,
      accurate: Boolean = true
  ): Either[ErrorMsg, Jpeg] =
    cache.get(videoUrl, elapsedTime) match
      case Some(jpeg) => Right(jpeg)
      case None       => grabFrame(videoUrl, elapsedTime, accurate)

  private def grabFrame(
      videoUrl: URL,
      elapsedTime: Duration,
      accurate: Boolean
  ): Either[ErrorMsg, Jpeg] =
    val jpeg = Jpeg.toPath(cache.root, videoUrl, elapsedTime)
    if (PathUtil.isChild(cache.root, jpeg.path))
      val parent = jpeg.path.getParent()
      if (!Files.exists(parent))
        Files.createDirectories(parent)
      FfmpegUtil.frameCapture(videoUrl, elapsedTime, jpeg.path, accurate) match
        case Left(e)     =>
          log
            .withCause(e)
            .atDebug
            .log(() =>
              s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUrl"
            )
          Left(StatusMsg(s"Failed to capture frame from $videoUrl at $elapsedTime", 500))
        case Right(path) =>
          val sizeBytes = Files.size(path)
          val theJpeg   = jpeg.copy(path = path, sizeBytes = Some(sizeBytes))
          cache.put(theJpeg)
          Right(theJpeg)
    else Left(ServerError("An invalid cache path was calculated"))
