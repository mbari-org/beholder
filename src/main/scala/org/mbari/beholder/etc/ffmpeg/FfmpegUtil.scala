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

import java.net.URI
import java.nio.file.{Files, Path}
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

    private val ffmpegExecutable: String =
        sys.props.getOrElse("beholder.ffmpeg.path", "ffmpeg")

    /** Cached, so repeated captures from the same video only shell out to ffprobe once. */
    private val ffprobe: Ffprobe = FfprobeService.default

    // Some cameras (e.g. older AJA on i2MAP) produce ProRes files where the ProRes bitstream
    // atoms carry no colorspace info, so the decoder reports prim:reserved and
    // trc:reserved even though the MOV container correctly says bt709.  When
    // swscaler sees reserved primaries/TRC it refuses all pixel-format conversions.
    // Detect that specific failure so we can retry with an explicit override.
    private val ReservedColorspacePattern = "prim:reserved|trc:reserved".r

    private def isReservedColorspaceError(err: Throwable): Boolean =
        ReservedColorspacePattern.findFirstIn(err.getMessage).isDefined

    /**
     * Build the `-vf` argument shared by every output format.
     *
     * `-apply_cropping 0` is what stops the decoder honoring a MOV clean aperture (clap) atom, but ffmpeg has only one
     * boolean for cropping: it also stops the decoder applying the codec's own crop. That matters for H.264/HEVC in
     * MP4, where the coded frame is padded up to the macroblock grid (1080 becomes 1088) and the padding rows decode to
     * Y=0,U=0,V=0, i.e. a green band across the bottom of the image. ProRes has no codec level crop, so it never showed
     * the problem.
     *
     * So we suppress cropping in the decoder and reinstate just the codec's crop here, using the size ffprobe reports
     * (which ignores the clap). `min()` keeps this safe if the decoded frame is ever smaller than the probe claims: the
     * crop clamps instead of failing.
     */
    private def buildVideoFilters(videoUri: URI, overrideColorspace: Boolean): Seq[String] =
        val cropFilter = ffprobe
            .videoSize(videoUri)
            .map(size => s"crop=min(iw\\,${size.width}):min(ih\\,${size.height}):0:0")

        // colorspace=iall=bt709 is a no-op for YUV→YUV same-csp paths and does
        // not update the filter-link prim/trc, so swscaler still sees reserved.
        // Route through an rgb24 intermediate: the YUV→RGB conversion forces the
        // colorspace filter to run properly and sets prim:bt709 on the link.
        val colorspaceFilter = Option.when(overrideColorspace)("colorspace=iall=bt709:all=bt709,format=rgb24")

        val filters = (cropFilter ++ colorspaceFilter).toSeq
        if filters.isEmpty then Seq.empty
        else Seq("-vf", filters.mkString(","))

    private def buildPngCommand(
        videoUri: URI,
        elapsedTime: Duration,
        target: Path,
        accurate: Boolean = true,
        skipNonKeyFrames: Boolean = false,
        overrideColorspace: Boolean = false
    ): Seq[String] =
        val time = DurationUtil.toHMS(elapsedTime)

        val vfArgs = buildVideoFilters(videoUri, overrideColorspace)

        Seq(ffmpegExecutable) ++
            Seq("-apply_cropping", "0") ++ // Suppress clap/clean-aperture crop at decoder level; must precede -i
            Seq("-ss", time) ++
            Option.when(skipNonKeyFrames)(Seq("-skip_frame", "nokey")).getOrElse(Seq.empty) ++
            Option.when(!accurate)(Seq("-noaccurate_seek")).getOrElse(Seq.empty) ++
            Seq("-i", videoUri.toString) ++
            Seq("-map", "0:v:0", "-frames:v", "1") ++
            vfArgs ++
            Seq(
                "-c:v",
                "png",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                target.toString
            )

    private def buildJpegCommand(
        videoUri: URI,
        elapsedTime: Duration,
        target: Path,
        accurate: Boolean = true,
        skipNonKeyFrames: Boolean = false,
        overrideColorspace: Boolean = false
    ): Seq[String] =
        val time = DurationUtil.toHMS(elapsedTime)

        val vfArgs = buildVideoFilters(videoUri, overrideColorspace)

        Seq(ffmpegExecutable) ++
            Seq("-apply_cropping", "0") ++  // Suppress clap/clean-aperture crop at decoder level; must precede -i
            Seq("-ss", time) ++             // Seek. This needs to be first. If it's after -i the capture is MUCH slower
            Option.when(skipNonKeyFrames)(Seq("-skip_frame", "nokey")).getOrElse(Seq.empty) ++
            Option.when(!accurate)(Seq("-noaccurate_seek")).getOrElse(Seq.empty) ++
            Seq("-i", videoUri.toString) ++ // input file or URL
            Seq(
                "-map",
                "0:v:0", // Explicitly select first video stream (avoids ambiguity when audio streams precede video)
                "-frames:v",
                "1"
            ) ++
            vfArgs ++
            Seq(
                "-qmin",
                "1",            //
                "-q:v",
                "1",            //
                "-hide_banner", // Make quiet
                "-loglevel",
                "error",        // Make quieter
                "-y",           // Automatically overwrites the output file if it already exists.
                target.toString // The output filename for the extracted frame.
            )

    private def runCommand(cmd: Seq[String]): Either[Throwable, Unit] =
        val stdout = new StringBuilder
        val stderr = new StringBuilder

        val logger = ProcessLogger(
            line => stdout.append(line).append(System.lineSeparator()),
            line => stderr.append(line).append(System.lineSeparator())
        )

        Try(Process(cmd).!(logger)) match
            case Failure(e) =>
                Left(
                    new RuntimeException(
                        s"""Failed to start ffmpeg process.
                           |
                           |Command:
                           |${cmd.mkString(" ")}
                           |
                           |Make sure ffmpeg is installed and available on PATH, or set:
                           |-Dbeholder.ffmpeg.path=/absolute/path/to/ffmpeg
                           |""".stripMargin,
                        e
                    )
                )

            case Success(exitCode) if exitCode == 0 =>
                Right(())

            case Success(exitCode) =>
                Left(
                    new RuntimeException(
                        s"""ffmpeg exited with non-zero status: $exitCode
                           |
                           |Command:
                           |${cmd.mkString(" ")}
                           |
                           |stdout:
                           |$stdout
                           |
                           |stderr:
                           |$stderr
                           |""".stripMargin
                    )
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
        def buildCmd(overrideColorspace: Boolean): Seq[String] =
            ImageType.fromPath(target) match
                case Some(ImageType.Jpeg) =>
                    buildJpegCommand(videoUri, elapsedTime, target, accurate, skipNonKeyFrames, overrideColorspace)
                case Some(ImageType.Png)  =>
                    buildPngCommand(videoUri, elapsedTime, target, accurate, skipNonKeyFrames, overrideColorspace)
                case _                    => Seq.empty

        val cmd = buildCmd(overrideColorspace = false)

        if cmd.isEmpty then Left(new IllegalArgumentException(s"Unsupported image type for target: $target"))
        else
            log.atDebug.log(() => s"Executing ${cmd.mkString(" ")}")
            Option(target.getParent).foreach(p => Files.createDirectories(p))

            runCommand(cmd) match
                case Left(err) if isReservedColorspaceError(err) =>
                    val fallback = buildCmd(overrideColorspace = true)
                    log.atDebug.log(() => s"Retrying with colorspace override: ${fallback.mkString(" ")}")
                    runCommand(fallback).map(_ => target)
                case Left(err)                                   => Left(err)
                case Right(())                                   => Right(target)
