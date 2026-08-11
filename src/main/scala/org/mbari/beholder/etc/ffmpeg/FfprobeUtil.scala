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

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import org.mbari.beholder.etc.jdk.Logging.given

import scala.util.{Failure, Success, Try}
import sys.process.*

/**
 * Utility functions for using ffprobe. Every call shells out; use [[FfprobeService]] if you want the results cached.
 */
object FfprobeUtil extends Ffprobe:
    private val log = System.getLogger(getClass.getName())

    private val ffprobeExecutable: String =
        sys.props.getOrElse("beholder.ffprobe.path", "ffprobe")

    /** Not a cache. Throttles the log so a missing ffprobe doesn't warn on every single capture. */
    private val warnedMissingFfprobe = AtomicBoolean(false)

    override def videoSize(videoUri: URI): Option[VideoSize] =
        val cmd = Seq(
            ffprobeExecutable,
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=width,height",
            "-of",
            "csv=p=0",
            videoUri.toString
        )

        val stdout = new StringBuilder
        val stderr = new StringBuilder
        val logger = ProcessLogger(
            line => stdout.append(line).append(System.lineSeparator()),
            line => stderr.append(line).append(System.lineSeparator())
        )

        log.atDebug.log(() => s"Executing ${cmd.mkString(" ")}")

        // Neither failure branch is fatal: the caller just captures without an explicit crop.
        Try(Process(cmd).!(logger)) match
            case Success(0)        => parse(stdout.toString)
            case Success(exitCode) =>
                log.atDebug.log(() => s"ffprobe exited with $exitCode for $videoUri. stderr: $stderr")
                None
            case Failure(e)        =>
                // Worth surfacing. Without ffprobe we can't crop off the codec's macroblock
                // padding, so H.264 captures come back with a green band along the bottom.
                if warnedMissingFfprobe.compareAndSet(false, true) then
                    log.atWarn
                        .withCause(e)
                        .log(
                            s"""Failed to start ffprobe.
                               |
                               |Captures will still work, but frames from H.264/HEVC videos will
                               |include the codec's padding rows (e.g. 1088 instead of 1080 tall).
                               |
                               |Make sure ffprobe is installed and available on PATH, or set:
                               |-Dbeholder.ffprobe.path=/absolute/path/to/ffprobe
                               |""".stripMargin
                        )
                None

    private def parse(csv: String): Option[VideoSize] =
        csv.linesIterator
            .map(_.trim)
            .filter(_.nonEmpty)
            .nextOption()
            .map(_.split(","))
            .collect { case Array(w, h) => (w.trim.toIntOption, h.trim.toIntOption) }
            .collect { case (Some(w), Some(h)) if w > 0 && h > 0 => VideoSize(w, h) }
