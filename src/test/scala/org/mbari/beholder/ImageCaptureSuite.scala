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
import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.mbari.beholder.etc.ffmpeg.{Ffprobe, FieldOrder, VideoInfo}
import scala.jdk.CollectionConverters.*

class ImageCaptureSuite extends LoggingFunSuite:

    val root    = TestUtil.root
    Files.createDirectories(root)
    val cache   = ImageCacheImpl(root, 3, .3)
    val capture = ImageCapture(cache)

    test("capture"):
        capture.capture(TestUtil.bigBuckBunny.toURI, Duration.ofMillis(1234)) match
            case Left(_)      => fail("Expected an image to be captured and it wasn't")
            case Right(jpeg0) => // Check values
                capture.capture(TestUtil.bigBuckBunny.toURI, Duration.ofMillis(1234)) match
                    case Left(_)      => fail("Expected an image to be in the cache")
                    case Right(jpeg1) =>
                        // Cache changes creationDate. So we can't just compare jpeg1 and jpeg2.
                        assertEquals(jpeg1.elapsedTime, jpeg0.elapsedTime)
                        assertEquals(jpeg1.path, jpeg0.path)
                        assertTrue(jpeg1.sizeBytes.isDefined)
                        assertTrue(jpeg0.sizeBytes.isDefined)
                        assertEquals(jpeg1.sizeBytes.get, jpeg0.sizeBytes.get)
                        assertEquals(jpeg1.videoUri, jpeg0.videoUri)

    // ---- request coalescing ----

    private val videoUri = URI.create("file:///videos/coalesce-me.mp4")

    /** A fresh cache per test, so nothing another test left on disk can decide the outcome. */
    private def freshCapture(grabber: FrameGrabber, ffprobe: Ffprobe = neverInterlaced): ImageCapture =
        val tempRoot = Files.createTempDirectory("beholder_coalesce_")
        tempRoot.toFile.deleteOnExit()
        ImageCapture(ImageCacheImpl(tempRoot, 100, .3), grabber, ffprobe)

    /** Stands in for ffmpeg: counts its calls, and is slow enough that callers genuinely overlap. */
    private def countingGrabber(counter: AtomicInteger, result: Path => Either[Throwable, Path]): FrameGrabber =
        (_, _, target, _, _, _) =>
            counter.incrementAndGet()
            Thread.sleep(300)
            result(target)

    /** Stands in for ffprobe, and counts how many times it was consulted. */
    private class FakeFfprobe(fieldOrder: FieldOrder) extends Ffprobe:
        val calls = AtomicInteger(0)

        override def probe(videoUri: URI): Option[VideoInfo] =
            calls.incrementAndGet()
            Some(VideoInfo(1920, 1080, fieldOrder))

    private def neverInterlaced: Ffprobe = FakeFfprobe(FieldOrder.Progressive)

    /** Records the deinterlace flag each capture was actually run with. */
    private def recordingGrabber(seen: java.util.concurrent.ConcurrentLinkedQueue[Boolean]): FrameGrabber =
        (_, _, target, _, _, deinterlace) =>
            seen.add(deinterlace)
            writeAFrame(target)

    private def writeAFrame(target: Path): Either[Throwable, Path] =
        Files.createDirectories(target.getParent)
        Files.writeString(target, "pretend this is a jpeg")
        Right(target)

    /** Fires `n` captures at once and collects what each one got back. */
    private def captureConcurrently[A](n: Int)(work: Int => A): Seq[A] =
        val pool  = Executors.newFixedThreadPool(n)
        val ready = CountDownLatch(1)
        try
            val futures = (1 to n).map(i =>
                pool.submit: () =>
                    ready.await()
                    work(i)
            )
            ready.countDown()
            futures.map(_.get(30, TimeUnit.SECONDS))
        finally pool.shutdownNow()

    /**
     * The whole point: an annotation UI asking 12 times for the same frame should cost one ffmpeg process, not 12 — and
     * 12 processes writing the same target path can hand back a half-written frame.
     */
    test("concurrent requests for the same frame run ffmpeg once and all get the result"):
        val grabs      = AtomicInteger(0)
        val theCapture = freshCapture(countingGrabber(grabs, writeAFrame))

        val results = captureConcurrently(12)(_ => theCapture.capture(videoUri, Duration.ofMillis(5000)))

        assertEquals(grabs.get(), 1, "ffmpeg should have run exactly once for 12 identical requests")
        assertTrue(s"Every caller should get an image, got $results", results.forall(_.isRight))
        assertEquals(results.map(_.map(_.path)).distinct.size, 1, "every caller should get the same frame")

    /** Coalescing must key on the frame, not act as one global lock. */
    test("concurrent requests for different frames each run their own capture"):
        val grabs      = AtomicInteger(0)
        val theCapture = freshCapture(countingGrabber(grabs, writeAFrame))

        val results = captureConcurrently(6)(i => theCapture.capture(videoUri, Duration.ofMillis(10000L + i)))

        assertEquals(grabs.get(), 6, "each distinct frame needs its own capture")
        assertTrue(s"Every caller should get an image, got $results", results.forall(_.isRight))

    /** A waiter must be told the capture failed, not left hanging on a leader that never delivers. */
    test("waiters get the leader's failure rather than each retrying"):
        val grabs      = AtomicInteger(0)
        val theCapture = freshCapture(countingGrabber(grabs, _ => Left(new RuntimeException("ffmpeg said no"))))

        val results = captureConcurrently(8)(_ => theCapture.capture(videoUri, Duration.ofMillis(15000)))

        assertEquals(grabs.get(), 1, "a failing capture should not be retried by every waiter at once")
        assertTrue(s"Every caller should see the failure, got $results", results.forall(_.isLeft))

    /** Failures are not cached, so the in-flight entry has to be cleared once the leader is done. */
    test("a failed capture is not remembered; a later request tries again"):
        val grabs      = AtomicInteger(0)
        val theCapture = freshCapture(countingGrabber(grabs, _ => Left(new RuntimeException("ffmpeg said no"))))

        assertTrue("first attempt should fail", theCapture.capture(videoUri, Duration.ofMillis(20000)).isLeft)
        assertTrue("second attempt should fail", theCapture.capture(videoUri, Duration.ofMillis(20000)).isLeft)

        assertEquals(grabs.get(), 2, "a later request must retry rather than reuse the failed attempt")

    // ---- deinterlacing ----

    private def deinterlaceFlagsOf(
        ffprobe: Ffprobe
    )(request: ImageCapture => Either[api.ErrorMsg, CachedImage]): (Seq[Boolean], Seq[CachedImage]) =
        val seen       = java.util.concurrent.ConcurrentLinkedQueue[Boolean]()
        val theCapture = freshCapture(recordingGrabber(seen), ffprobe)
        val result     = request(theCapture)
        assertTrue(s"capture should have succeeded, got $result", result.isRight)
        (seen.asScala.toSeq, result.toSeq)

    test("an interlaced video asked to be deinterlaced is deinterlaced, and filed as such"):
        val (flags, images) = deinterlaceFlagsOf(FakeFfprobe(FieldOrder.Tb)):
            _.capture(videoUri, Duration.ofMillis(1000), deinterlace = true)
        assertEquals(flags, Seq(true), "ffmpeg should have been asked to deinterlace")
        assertTrue(images.head.deinterlace)
        assertTrue(
            s"${images.head.path} should be marked deinterlaced",
            images.head.path.getFileName.toString.endsWith("_deinterlaced.jpg")
        )

    /**
     * Deinterlacing a progressive video produces the same pixels as not deinterlacing it, so the request resolves to
     * false and the frame shares the ordinary entry rather than being duplicated under another name.
     */
    test("a progressive video asked to be deinterlaced is captured normally"):
        val (flags, images) = deinterlaceFlagsOf(FakeFfprobe(FieldOrder.Progressive)):
            _.capture(videoUri, Duration.ofMillis(1000), deinterlace = true)
        assertEquals(flags, Seq(false), "there is nothing to deinterlace")
        assertTrue(!images.head.deinterlace)
        assertEquals(images.head.path.getFileName.toString, "00_00_01.000.jpg")

    test("a video that cannot be probed is captured normally"):
        val unprobeable     = new Ffprobe:
            override def probe(videoUri: URI): Option[VideoInfo] = None
        val (flags, images) = deinterlaceFlagsOf(unprobeable):
            _.capture(videoUri, Duration.ofMillis(1000), deinterlace = true)
        assertEquals(flags, Seq(false), "an unprobeable video must not be deinterlaced on spec")
        assertTrue(!images.head.deinterlace)

    /** The frames a deinterlacer would see under nokey are whole keyframes apart, so the result is not worth having. */
    test("nokey suppresses deinterlacing, without even probing"):
        val probe           = FakeFfprobe(FieldOrder.Tb)
        val (flags, images) = deinterlaceFlagsOf(probe):
            _.capture(videoUri, Duration.ofMillis(1000), skipNonKeyFrames = true, deinterlace = true)
        assertEquals(flags, Seq(false), "nokey wins over a deinterlace request")
        assertTrue(!images.head.deinterlace)
        assertEquals(probe.calls.get(), 0, "a request that cannot be deinterlaced should not pay for a probe")

    test("not asking for deinterlacing costs no probe at all"):
        val probe      = FakeFfprobe(FieldOrder.Tb)
        val (flags, _) = deinterlaceFlagsOf(probe)(_.capture(videoUri, Duration.ofMillis(1000)))
        assertEquals(flags, Seq(false))
        assertEquals(probe.calls.get(), 0, "the default path must not have gained an ffprobe call")

    test("the deinterlaced and ordinary captures of one frame are separate cache entries"):
        val seen       = java.util.concurrent.ConcurrentLinkedQueue[Boolean]()
        val theCapture = freshCapture(recordingGrabber(seen), FakeFfprobe(FieldOrder.Tb))
        val elapsed    = Duration.ofMillis(2000)

        val plain = theCapture.capture(videoUri, elapsed)
        val deint = theCapture.capture(videoUri, elapsed, deinterlace = true)

        assertEquals(seen.asScala.toSeq, Seq(false, true), "each variant needs its own capture")
        assertNotEquals(plain.map(_.path), deint.map(_.path))

        // ...and each is served from the cache on a second ask, without another capture
        theCapture.capture(videoUri, elapsed)
        theCapture.capture(videoUri, elapsed, deinterlace = true)
        assertEquals(seen.size(), 2, "both variants should now be cached")

    /** Coalescing keys on the target path, so it must not merge two requests that produce different pixels. */
    test("concurrent requests differing only in deinterlace are not coalesced together"):
        val grabs      = AtomicInteger(0)
        val theCapture = freshCapture(countingGrabber(grabs, writeAFrame), FakeFfprobe(FieldOrder.Tb))

        val results = captureConcurrently(12): i =>
            theCapture.capture(videoUri, Duration.ofMillis(25000), deinterlace = i % 2 == 0)

        assertEquals(grabs.get(), 2, "one capture per variant, not one for all twelve")
        assertTrue(s"Every caller should get an image, got $results", results.forall(_.isRight))
        assertEquals(results.map(_.map(_.path)).distinct.size, 2, "callers should get the variant they asked for")
