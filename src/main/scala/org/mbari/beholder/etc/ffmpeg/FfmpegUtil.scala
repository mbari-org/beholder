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

import org.mbari.beholder.ImageType

import java.net.{URI, URL}
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

    private def buildPngCommand(videoUri: URI,
                                elapsedTime: Duration,
                                target: Path,
                                accurate: Boolean = true,
                                skipNonKeyFrames: Boolean = false): Seq[String] =
        val time = DurationUtil.toHMS(elapsedTime)

        Seq("ffmpeg") ++
                Seq("-ss", time) ++
                Option.when(skipNonKeyFrames)(Seq("-skip_frame", "nokey")).getOrElse(Seq.empty) ++
                Option.when(!accurate)(Seq("-noaccurate_seek")).getOrElse(Seq.empty) ++
                Seq(
                    "-i", videoUri.toString,
                    "-frames:v", "1",
                    "-c:v", "png",
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    target.toString
                )

    private def buildJpegCommand(videoUri: URI,
                                 elapsedTime: Duration,
                                 target: Path,
                                 accurate: Boolean = true,
                                 skipNonKeyFrames: Boolean = false): Seq[String] =
        val time = DurationUtil.toHMS(elapsedTime)

        Seq("ffmpeg") ++
            Seq("-ss", time) ++ // Seek. This needs to be first. If it's after -i the capture is MUCH slower
            Option.when(skipNonKeyFrames)(Seq("-skip_frame", "nokey")).getOrElse(Seq.empty) ++
            Option.when(!accurate)(Seq("-noaccurate_seek")).getOrElse(Seq.empty) ++
            Seq(
                "-i", videoUri.toString, // input file or URL
                "-frames:v", "1",        // Frame quality 1 (best) to 5
                "-qmin", "1",            //
                "-q:v", "1",             //
                "-hide_banner",          // Make quiet
                "-loglevel", "error",    // Make quieter
                "-y",                    // Automatically overwrites the output file if it already exists.
                target.toString          // The output filename for the extracted frame.
            )


    /**
     * Capture a frame from a video at a given time and save it to a file.
     * @param videoUri
     *   The video to fetch from
     * @param elapsedTime
     *   The time into the video to grab a frame
     * @param target
     *   The location to save the image to
     * @param accurate
     *   By default ffmpeg will return "frame accutrate" capture. If you want the nearest preceding keyframe, use false
     */
    def frameCapture(
                        videoUri: URI,
                        elapsedTime: Duration,
                        target: Path,
                        accurate: Boolean = true,
                        skipNonKeyFrames: Boolean = false
    ): Either[Throwable, Path] =
        val cmd: Seq[String] = ImageType.fromPath(target) match
            case Some(ImageType.Jpeg) => buildJpegCommand(videoUri, elapsedTime, target, accurate, skipNonKeyFrames)
            case Some(ImageType.Png)  => buildPngCommand(videoUri, elapsedTime, target, accurate, skipNonKeyFrames)
            case _                    => Seq.empty

        if cmd.isEmpty then
            Left(new IllegalArgumentException(s"Unsupported image type for target: $target"))
        else
            log.atDebug.log(() => s"Executing ${cmd.mkString(" ")}")
            Try(Process(cmd).!!).map(_ => target).toEither


            /*
      Argument Breakdown:
ffmpeg The command-line tool for processing video and audio files.

-ss "00:12:18.521" - Seeks to the timestamp 12 minutes, 18.521 seconds before
starting processing. When used before -i, it seeks quickly (keyframe-based)
rather than frame-accurate.

-noaccurate_seek - Prevents precise (slow) seeking. Instead, seeking happens
at the nearest keyframe, making it faster but potentially less accurate.

-skip_frame nokey - Skips non-keyframes, meaning only keyframes will be
considered. Useful for fast processing when exact frame accuracy is not required.

-i "http://m3.shore.mbari.org/...V4430_20220908T155336Z_h264.mp4" -
Specifies the input video file. In this case, it is a remote .mp4 video file.

-frames:v 1 - Extracts exactly one video frame.

-qmin 1 - Sets the minimum quality factor for JPEG encoding (1 is the highest quality).

-q:v 1 - Sets the output image quality for the extracted frame. Lower values
mean better quality (1 is best for JPEG).

-hide_banner - Suppresses extra information about the FFmpeg version.

-loglevel error - Only displays errors in the output (hides warnings and other messages).

-y - Automatically overwrites the output file if it already exists.

The output filename for the extracted frame.

             */
