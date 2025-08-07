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
import scala.util.Try
import scala.util.Failure
import scala.util.Success

class JpegCapture(cache: JpegCache):

    private val log = System.getLogger(getClass.getName)

    /**
      * Capture a frame from the video at the specified elapsed time. If the frame is 
      * not already in the cache, it will be captured using ffmpeg and stored in the cache.
      *
      * @param videoUrl The URL of the video to capture from
      * @param elapsedTime The elapsed time into the video to capture the frame
      * @param accurate If true, the frame will be captured at the exact elapsed time. If false, the frame will be captured at the nearest keyframe.
      * @param skipNonKeyFrames If true, the capture will skip non-key frames. This is useful for videos that do not have keyframes at regular intervals.
      * @return On success, a Right containing the information and location on disk of the captured Jpeg. On failure, a Left containing an ErrorMsg.
      */
    def capture(
        videoUrl: URL,
        elapsedTime: Duration,
        accurate: Boolean = true,
        skipNonKeyFrames: Boolean = false
    ): Either[ErrorMsg, Jpeg] =
        cache.get(videoUrl, elapsedTime) match
            case Some(jpeg) => Right(jpeg)
            case None       => grabFrame(videoUrl, elapsedTime, accurate, skipNonKeyFrames)

    private def grabFrame(
        videoUrl: URL,
        elapsedTime: Duration,
        accurate: Boolean,
        skipNonKeyFrames: Boolean
    ): Either[ErrorMsg, Jpeg] =
        val jpeg = Jpeg.toPath(cache.root, videoUrl, elapsedTime)
        if PathUtil.isChild(cache.root, jpeg.path) then
            val parent = jpeg.path.getParent()
            if !Files.exists(parent) then Files.createDirectories(parent)
            FfmpegUtil.frameCapture(videoUrl, elapsedTime, jpeg.path, accurate, skipNonKeyFrames) match
                case Left(e)     =>
                    log
                        .withCause(e)
                        .atDebug
                        .log(() => s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUrl")
                    Left(StatusMsg(s"Failed to capture frame from $videoUrl at $elapsedTime", 500))
                case Right(path) =>
                    Try:
                        val sizeBytes = Files.size(path)
                        val theJpeg   = jpeg.copy(path = path, sizeBytes = Some(sizeBytes))
                        cache.put(theJpeg)
                        log.atDebug.log(() => s"Captured image at ${DurationUtil.toHMS(elapsedTime)} from $videoUrl")
                        theJpeg
                    match
                        case Failure(exception) => 
                            log
                                .withCause(exception)
                                .atError
                                .log(() => s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUrl")
                            Left(StatusMsg(s"Failed to capture frame from $videoUrl at $elapsedTime", 500))
                        case Success(value) => Right(value)
                    
        else Left(ServerError("An invalid cache path was calculated"))
