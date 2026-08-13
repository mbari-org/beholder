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

import org.mbari.beholder.AppConfig

import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.etc.jdk.ProcessRunner

/**
 * Utility functions for using ffprobe. Every call shells out; use [[FfprobeService]] if you want the results cached.
 *
 * @param timeout
 *   How long a probe may run before it is killed. A probe runs on the same pool as the capture that follows it, so an
 *   unreachable video must not be allowed to pin that thread indefinitely.
 */
class FfprobeUtil(timeout: Duration = AppConfig.Ffprobe.Timeout) extends Ffprobe:
    private val log = System.getLogger(getClass.getName())

    private val ffprobeExecutable: String = AppConfig.Ffprobe.Path

    /** Not a cache. Throttles the log so a missing ffprobe doesn't warn on every single capture. */
    private val warnedMissingFfprobe = AtomicBoolean(false)

    override def probe(videoUri: URI): Option[VideoInfo] =
        val cmd = Seq(
            ffprobeExecutable,
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=width,height,field_order",
            "-of",
            "csv=p=0",
            videoUri.toString
        )

        log.atDebug.log(() => s"Executing ${cmd.mkString(" ")}")

        // No failure branch here is fatal: the caller just captures without an explicit crop.
        ProcessRunner.run(cmd, timeout) match
            case Right(result) if result.exitCode == 0 => parse(result.stdout)

            case Right(result) =>
                log.atDebug.log(() => s"ffprobe exited with ${result.exitCode} for $videoUri. stderr: ${result.stderr}")
                None

            case Left(_: TimeoutException) =>
                // Already logged by ProcessRunner, which knows the process had to be killed.
                None

            case Left(e) =>
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

    /**
     * Parse `width,height,field_order`, e.g. `1920,1080,tb`.
     *
     * The field order is optional in a way the size is not: a stream that does not declare one still gives us a usable
     * crop, so a missing third column degrades to [[FieldOrder.Unknown]] rather than failing the whole probe. `split`
     * drops trailing empty fields, so that case arrives here as a two element array.
     */
    private def parse(csv: String): Option[VideoInfo] =
        csv.linesIterator
            .map(_.trim)
            .filter(_.nonEmpty)
            .nextOption()
            .map(_.split(","))
            .collect:
                case Array(w, h)    => (w.trim.toIntOption, h.trim.toIntOption, FieldOrder.Unknown)
                case Array(w, h, f) => (w.trim.toIntOption, h.trim.toIntOption, FieldOrder.parse(f))
            .collect { case (Some(w), Some(h), fieldOrder) if w > 0 && h > 0 => VideoInfo(w, h, fieldOrder) }

/** The configured instance. [[FfprobeService.default]] wraps this one in its cache. */
object FfprobeUtil extends FfprobeUtil(AppConfig.Ffprobe.Timeout)
