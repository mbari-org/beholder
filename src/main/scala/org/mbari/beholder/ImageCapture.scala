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

class ImageCapture(cache: ImageCache):

    private val log = System.getLogger(getClass.getName)

    /**
     * Capture a frame from the video at the specified elapsed time. If the frame is not already in the cache, it will
     * be captured using ffmpeg and stored in the cache.
     *
     * @param videoUri
     *   The URL of the video to capture from
     * @param elapsedTime
     *   The elapsed time into the video to capture the frame
     * @param accurate
     *   If true, the frame will be captured at the exact elapsed time. If false, the frame will be captured at the
     *   nearest keyframe.
     * @param skipNonKeyFrames
     *   If true, the capture will skip non-key frames. This is useful for videos that do not have keyframes at regular
     *   intervals.
     * @param imageType
     *   The type of image to capture.
     * @return
     *   On success, a Right containing the information and location on disk of the captured Jpeg. On failure, a Left
     *   containing an ErrorMsg.
     */
    def capture(
        videoUri: URI,
        elapsedTime: Duration,
        accurate: Boolean = true,
        skipNonKeyFrames: Boolean = false,
        imageType: ImageType = ImageType.Jpeg
    ): Either[ErrorMsg, CachedImage] =
        cache.get(videoUri, elapsedTime, imageType) match
            case Some(cachedImage) => Right(cachedImage)
            case None              => grabFrame(videoUri, elapsedTime, accurate, skipNonKeyFrames, imageType)

    private def grabFrame(
        videoUri: URI,
        elapsedTime: Duration,
        accurate: Boolean,
        skipNonKeyFrames: Boolean,
        imageType: ImageType = ImageType.Jpeg
    ): Either[ErrorMsg, CachedImage] =
        val cachedImage = CachedImage.toPath(cache.root, videoUri, elapsedTime, imageType = imageType)
        if PathUtil.isChild(cache.root, cachedImage.path) then
            val parent = cachedImage.path.getParent()
            if !Files.exists(parent) then Files.createDirectories(parent)
            FfmpegUtil.frameCapture(videoUri, elapsedTime, cachedImage.path, accurate, skipNonKeyFrames) match
                case Left(e)     =>
                    log
                        .withCause(e)
                        .atDebug
                        .log(() => s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUri")
                    Left(StatusMsg(s"Failed to capture frame from $videoUri at $elapsedTime", 500))
                case Right(path) =>
                    Try:
                        val sizeBytes = Files.size(path)
                        val theImage   = cachedImage.copy(path = path, sizeBytes = Some(sizeBytes))
                        cache.put(theImage)
                        log.atDebug.log(() => s"Captured image (${imageType.mediaType} at ${DurationUtil.toHMS(elapsedTime)} from $videoUri")
                        theImage
                    match
                        case Failure(exception) =>
                            log
                                .withCause(exception)
                                .atError
                                .log(() =>
                                    s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUri"
                                )
                            Left(StatusMsg(s"Failed to capture frame from $videoUri at $elapsedTime", 500))
                        case Success(value)     => Right(value)
        else Left(ServerError("An invalid cache path was calculated"))
