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

class ImageCaptureSuite extends munit.FunSuite:

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
    private def freshCapture(grabber: FrameGrabber): ImageCapture =
        val tempRoot = Files.createTempDirectory("beholder_coalesce_")
        tempRoot.toFile.deleteOnExit()
        ImageCapture(ImageCacheImpl(tempRoot, 100, .3), grabber)

    /** Stands in for ffmpeg: counts its calls, and is slow enough that callers genuinely overlap. */
    private def countingGrabber(counter: AtomicInteger, result: Path => Either[Throwable, Path]): FrameGrabber =
        (_, _, target, _, _) =>
            counter.incrementAndGet()
            Thread.sleep(300)
            result(target)

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
