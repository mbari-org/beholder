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

import java.time.Duration
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.mbari.beholder.TestUtil

class FfmpegUtilSuite extends munit.FunSuite:

    private val videoUri = TestUtil.bigBuckBunny.toURI

    /** The dimensions the test video declares. Captures must match these exactly. */
    private val videoWidth  = 1920
    private val videoHeight = 1080

    test("frameCapture_jpg"):
        val path = Paths.get("target", "trashme.jpg")
        FfmpegUtil.frameCapture(videoUri, Duration.ofMillis(250), path) match
            case Left(e)  =>
                fail(s"File was not created at $path")
            case Right(v) =>
                val exists = Files.exists(path)
                assertTrue(s"File was not created at $path", exists)
        if Files.exists(path) then Files.delete(path)

    test("frameCapture_png"):
        val path = Paths.get("target", "trashme.png")
        FfmpegUtil.frameCapture(videoUri, Duration.ofMillis(250), path) match
            case Left(e)  =>
                fail(s"File was not created at $path")
            case Right(v) =>
                val exists = Files.exists(path)
                assertTrue(s"File was not created at $path", exists)
        if Files.exists(path) then Files.delete(path)

    /**
     * -apply_cropping 0 suppresses the clean-aperture (clap) crop, but it also suppresses the codec's own crop. H.264
     * pads 1080 up to the 1088 macroblock boundary, and those 8 padding rows are the green band that shows up at the
     * bottom of MP4 captures. A capture must be exactly the size the video declares.
     */
    private def assertCaptureIsFullFrame(extension: String): Unit =
        val path = Paths.get("target", s"trashme-size.$extension")
        try
            FfmpegUtil.frameCapture(videoUri, Duration.ofMillis(250), path) match
                case Left(e)  =>
                    fail(s"Capture failed: ${e.getMessage}")
                case Right(_) =>
                    assertTrue(s"Capture file was not created at $path", Files.exists(path))
                    val image = Option(ImageIO.read(path.toFile)).getOrElse(
                        fail(s"Could not read .$extension capture at $path")
                    )
                    assertEquals(image.getWidth, videoWidth, s"Wrong width for .$extension capture")
                    assertEquals(image.getHeight, videoHeight, s"Wrong height for .$extension capture")
        finally if Files.exists(path) then Files.delete(path)

    test("frameCapture_jpg has no macroblock padding"):
        assertCaptureIsFullFrame("jpg")

    test("frameCapture_png has no macroblock padding"):
        assertCaptureIsFullFrame("png")

    /**
     * A capture that never finishes must not pin its thread forever. ffmpeg cannot start, seek and encode in a
     * millisecond, so this always trips the timeout.
     */
    test("frameCapture gives up and fails when ffmpeg exceeds its timeout"):
        val path = Paths.get("target", "trashme-timeout.jpg")
        try
            val result = FfmpegUtil.frameCapture(videoUri, Duration.ofMillis(250), path, timeout = Duration.ofMillis(1))
            assertTrue(s"Expected a timeout failure, got $result", result.isLeft)
        finally if Files.exists(path) then Files.delete(path)

    /**
     * A killed ffmpeg can leave a half-written frame at the target path. Nothing downstream can tell that apart from a
     * good capture — scanCache would index it as a real cache entry on the next restart — so a failed capture must not
     * leave a file behind.
     */
    test("frameCapture removes the output file when the capture fails"):
        val path = Paths.get("target", "trashme-partial.jpg")
        Files.writeString(path, "a truncated frame")
        try
            val result = FfmpegUtil.frameCapture(videoUri, Duration.ofMillis(250), path, timeout = Duration.ofMillis(1))
            assertTrue(s"Expected the capture to fail, got $result", result.isLeft)
            assertTrue(s"A failed capture left $path behind", !Files.exists(path))
        finally if Files.exists(path) then Files.delete(path)
