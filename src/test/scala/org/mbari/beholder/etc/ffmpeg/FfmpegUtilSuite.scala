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

    // ---- deinterlacing ----

    private val interlacedUri = TestUtil.interlaced.toURI

    /**
     * How much vertical high-frequency energy an image carries, as a rough stand-in for "how combed is it".
     *
     * Interlacing weaves two fields half a frame apart into one image, so anything that moved shows up as strong
     * alternating differences between neighbouring rows. A vertical Laplacian, `|2·p(y) − p(y−1) − p(y+1)|`, is large
     * exactly where that happens, so deinterlacing should drop this substantially. Real detail also contributes, which
     * is why the assertions below compare two captures of the *same* frame rather than testing against a fixed number.
     */
    private def combiness(path: java.nio.file.Path): Double =
        val image  = Option(ImageIO.read(path.toFile)).getOrElse(fail(s"Could not read capture at $path"))
        val width  = image.getWidth
        val height = image.getHeight

        def luma(x: Int, y: Int): Int =
            val rgb = image.getRGB(x, y)
            ((rgb >> 16 & 0xff) * 299 + (rgb >> 8 & 0xff) * 587 + (rgb & 0xff) * 114) / 1000

        var total = 0L
        for
            y <- 1 until height - 1
            x <- 0 until width
        do total += math.abs(2 * luma(x, y) - luma(x, y - 1) - luma(x, y + 1))
        total.toDouble / (width * (height - 2))

    private def captureInterlaced(extension: String, deinterlace: Boolean): java.nio.file.Path =
        val path = Paths.get("target", s"trashme-deinterlace-$deinterlace.$extension")
        FfmpegUtil.frameCapture(
            interlacedUri,
            Duration.ofMillis(500),
            path,
            deinterlace = deinterlace
        ) match
            case Left(e)  => fail(s"Capture failed: ${e.getMessage}")
            case Right(_) =>
                assertTrue(s"Capture file was not created at $path", Files.exists(path))
                path

    test("the test fixture really is interlaced"):
        assertEquals(FfprobeUtil.probe(interlacedUri).map(_.fieldOrder), Some(FieldOrder.Tt))
        assertTrue("the fixture should be reported as interlaced", FfprobeUtil.isInterlaced(interlacedUri))

    /**
     * The real check that this feature does anything at all: the same frame of the same interlaced video, captured with
     * and without the filter, must come back visibly less combed.
     */
    test("deinterlacing an interlaced video removes the combing"):
        val plain = captureInterlaced("png", deinterlace = false)
        val deint = captureInterlaced("png", deinterlace = true)
        try
            val plainComb = combiness(plain)
            val deintComb = combiness(deint)
            // Measured at 7.44 -> 5.50 on ffmpeg 8.1.2, a 26% drop. The bar is set well below that because
            // the exact number depends on the bwdif implementation; what has to hold is that the filter did
            // something. With the filter disabled the two captures are byte-identical, i.e. a ratio of 1.0,
            // so any threshold under 1.0 catches a regression.
            assertTrue(
                s"Deinterlacing should reduce vertical combing, but it went from $plainComb to $deintComb",
                deintComb < plainComb * 0.9
            )
        finally
            Files.deleteIfExists(plain)
            Files.deleteIfExists(deint)

    private def assertDeinterlacePreservesSize(extension: String): Unit =
        val plain = captureInterlaced(extension, deinterlace = false)
        val deint = captureInterlaced(extension, deinterlace = true)
        try
            val before = ImageIO.read(plain.toFile)
            val after  = ImageIO.read(deint.toFile)
            assertEquals(after.getWidth, before.getWidth, s"Wrong width for deinterlaced .$extension capture")
            assertEquals(after.getHeight, before.getHeight, s"Wrong height for deinterlaced .$extension capture")
            assertEquals(after.getWidth, 720)
            assertEquals(after.getHeight, 480)
        finally
            Files.deleteIfExists(plain)
            Files.deleteIfExists(deint)

    /** bwdif's default mode emits one frame per field; the wrong one would change the frame, not just filter it. */
    test("deinterlacing a jpg keeps the frame's dimensions"):
        assertDeinterlacePreservesSize("jpg")

    test("deinterlacing a png keeps the frame's dimensions"):
        assertDeinterlacePreservesSize("png")

    test("deinterlacing a progressive video still produces a usable frame"):
        val path = Paths.get("target", "trashme-deinterlace-progressive.png")
        try
            FfmpegUtil.frameCapture(videoUri, Duration.ofMillis(250), path, deinterlace = true) match
                case Left(e)  => fail(s"Capture failed: ${e.getMessage}")
                case Right(_) =>
                    val image = Option(ImageIO.read(path.toFile)).getOrElse(fail(s"Could not read capture at $path"))
                    assertEquals(image.getWidth, videoWidth)
                    assertEquals(image.getHeight, videoHeight)
        finally Files.deleteIfExists(path)

    /**
     * Verified by hand at the ffmpeg level: -frames:v 1 caps output frames, not decodes, so bwdif still gets its
     * neighbouring frame. These combinations must therefore still produce a frame.
     */
    test("deinterlacing works alongside the other capture flags"):
        for (accurate, skipNonKeyFrames) <- Seq((true, false), (false, false), (true, true)) do
            val path = Paths.get("target", s"trashme-di-$accurate-$skipNonKeyFrames.png")
            try
                FfmpegUtil.frameCapture(
                    interlacedUri,
                    Duration.ofMillis(500),
                    path,
                    accurate = accurate,
                    skipNonKeyFrames = skipNonKeyFrames,
                    deinterlace = true
                ) match
                    case Left(e)  =>
                        fail(s"Capture failed for accurate=$accurate nokey=$skipNonKeyFrames: ${e.getMessage}")
                    case Right(_) =>
                        val image = Option(ImageIO.read(path.toFile)).getOrElse(fail(s"Could not read $path"))
                        assertEquals(image.getWidth, 720)
                        assertEquals(image.getHeight, 480)
            finally Files.deleteIfExists(path)
