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

package org.mbari.beholder.api

import org.mbari.beholder.LoggingFunSuite
import org.mbari.beholder.{AppConfig, ImageCacheImpl, ImageCapture, ImageType, TestUtil}

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.mbari.beholder.etc.circe.CirceCodecs.{*, given}
import org.mbari.beholder.etc.jdk.BoundedExecutor

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import sttp.client3.*
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.TapirStubInterpreter

class CaptureEndpointsSuite extends LoggingFunSuite:

    given ExecutionContextExecutor = ExecutionContext.global

    private val root            = TestUtil.root
    Files.createDirectories(root)
    private val cache           = ImageCacheImpl(root, 3, .3)
    private val capture         = ImageCapture(cache)
    private val videoUrl        = TestUtil.bigBuckBunny
    private val captureEndpoint = CaptureEndpoints(capture, AppConfig.Api.Key)

    private def stub(impl: sttp.tapir.server.ServerEndpoint[Any, Future]): SttpBackend[Future, Any] =
        TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
            .whenServerEndpoint(impl)
            .thenRunLogic()
            .backend()

    private val captureStub    = stub(captureEndpoint.captureImpl)
    private val captureJpgStub = stub(captureEndpoint.captureJpgImpl)
    private val capturePngStub = stub(captureEndpoint.capturePngImpl)

    private def await(f: Future[Response[Either[String, String]]]) =
        Await.result(f, Duration(10, TimeUnit.SECONDS))

    // ---- /capture ----

    test("/capture"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture with invalid X-Api-Key"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture")
                .header("X-Api-Key", "bad key")
                .body(req.stringify)
                .send(captureStub)
        )
        assertEquals(result.code.code, 401)

    test("/capture?accurate=false"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture?accurate=false")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture?nokey=true"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture?nokey=true")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture with imageType jpg"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 3456L, Some(ImageType.Jpeg))
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureStub)
        )
        assertEquals(result.code.code, 200)
        assertEquals(result.header("Content-Type"), Some("image/jpeg"))

    test("/capture with imageType png"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 3456L, Some(ImageType.Png))
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureStub)
        )
        assertEquals(result.code.code, 200)
        assertEquals(result.header("Content-Type"), Some("image/png"))

    // ---- /capture/jpg ----

    test("/capture/jpg"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/jpg")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureJpgStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture/jpg with invalid X-Api-Key"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/jpg")
                .header("X-Api-Key", "bad key")
                .body(req.stringify)
                .send(captureJpgStub)
        )
        assertEquals(result.code.code, 401)

    test("/capture/jpg?accurate=false"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/jpg?accurate=false")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureJpgStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture/jpg?nokey=true"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/jpg?nokey=true")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(captureJpgStub)
        )
        assertEquals(result.code.code, 200)

    // ---- /capture/png ----

    test("/capture/png"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/png")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(capturePngStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture/png with invalid X-Api-Key"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/png")
                .header("X-Api-Key", "bad key")
                .body(req.stringify)
                .send(capturePngStub)
        )
        assertEquals(result.code.code, 401)

    test("/capture/png?accurate=false"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/png?accurate=false")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(capturePngStub)
        )
        assertEquals(result.code.code, 200)

    test("/capture/png?nokey=true"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(
            basicRequest
                .post(uri"http://test.com/capture/png?nokey=true")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(capturePngStub)
        )
        assertEquals(result.code.code, 200)

    // ---- load shedding ----

    /**
     * A shed request is the one case where the client is expected to come back, so the 503 has to say when in a form a
     * client can act on. The body already says "retry shortly" in prose that nothing parses.
     */
    private def assertRetryAfterHeader(response: Response[?]): Unit =
        val header = response.header("Retry-After")
        val advice = header.flatMap(_.toIntOption)
        assert(
            advice.exists(s =>
                s >= ServiceUnavailable.MinRetryAfterSeconds && s <= ServiceUnavailable.MaxRetryAfterSeconds
            ),
            s"Expected a Retry-After header of ${ServiceUnavailable.MinRetryAfterSeconds}.." +
                s"${ServiceUnavailable.MaxRetryAfterSeconds} seconds, got ${header.getOrElse("no header at all")}"
        )

    /**
     * ffmpeg is the scarce resource, so a burst bigger than the pool has to be turned away at the door. Queueing it
     * instead would mean running captures for clients that gave up long ago, while starving everything else.
     */
    test("/capture returns 503 when the capture pool has no room left"):
        val busy    = BoundedExecutor("test-capture", threads = 1, queueSize = 1)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try
            busy.submit { started.countDown(); release.await() }
            assert(started.await(10, TimeUnit.SECONDS), "the blocking task never started")
            assert(busy.submit(()).isDefined, "the queue slot should have been filled")

            val saturated = stub(CaptureEndpoints(capture, AppConfig.Api.Key, busy).captureImpl)
            val req       = CaptureRequest(videoUrl.toExternalForm(), 9876L)
            val result    =
                await(
                    basicRequest
                        .post(uri"http://test.com/capture")
                        .header("X-Api-Key", AppConfig.Api.Key)
                        .body(req.stringify)
                        .send(saturated)
                )
            assertEquals(result.code.code, 503)
            assertRetryAfterHeader(result)
        finally
            release.countDown()
            busy.shutdown()

    /**
     * The other half of shedding load. A request accepted into the queue can still turn out to be worthless by the time
     * a worker frees up, and running it then would spend an ffmpeg slot on a client that has already gone. The endpoint
     * has to answer 503 for that too — the caller's situation is identical to a full queue, and a 500 would tell them
     * to report a bug rather than retry.
     */
    test("/capture returns 503 when the request waited too long for a worker"):
        // java.time.Duration, spelled out: this file's bare `Duration` is the scala.concurrent one.
        val slow    = BoundedExecutor("test-capture", threads = 1, queueSize = 2, maxWait = java.time.Duration.ofMillis(1))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try
            slow.submit { started.countDown(); release.await() }
            assert(started.await(10, TimeUnit.SECONDS), "the blocking task never started")

            val backedUp = stub(CaptureEndpoints(capture, AppConfig.Api.Key, slow).captureImpl)
            val req      = CaptureRequest(videoUrl.toExternalForm(), 9876L)
            val sent     = basicRequest
                .post(uri"http://test.com/capture")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(req.stringify)
                .send(backedUp)

            // The request is queued behind the blocked worker. Let its deadline lapse before
            // freeing the worker, so it is stale by the time one looks at it.
            Thread.sleep(100)
            release.countDown()

            val result = await(sent)
            assertEquals(result.code.code, 503)
            assertRetryAfterHeader(result)
        finally
            release.countDown()
            slow.shutdown()

    // ---- deinterlace ----

    /** Records what the endpoint asked for, so we can tell the body was actually read. */
    private class RecordingCapture extends ImageCapture(ImageCacheImpl(root, 3, .3)):
        val seen = java.util.concurrent.ConcurrentLinkedQueue[Boolean]()

        override def capture(
            videoUri: java.net.URI,
            elapsedTime: java.time.Duration,
            accurate: Boolean,
            skipNonKeyFrames: Boolean,
            imageType: ImageType,
            deinterlace: Boolean
        ) =
            seen.add(deinterlace)
            super.capture(videoUri, elapsedTime, accurate, skipNonKeyFrames, imageType, deinterlace)

    private def deinterlaceSeenBy(body: String, path: String = "capture"): Boolean =
        val recording = RecordingCapture()
        val result    = await(
            basicRequest
                .post(uri"http://test.com/$path")
                .header("X-Api-Key", AppConfig.Api.Key)
                .body(body)
                .send(stub(CaptureEndpoints(recording, AppConfig.Api.Key).captureImpl))
        )
        assertEquals(result.code.code, 200)
        assertEquals(recording.seen.size(), 1)
        recording.seen.peek()

    test("/capture reads deinterlace from the request body"):
        val req = CaptureRequest(videoUrl.toExternalForm(), 3456L, deinterlace = Some(true))
        assert(deinterlaceSeenBy(req.stringify), "deinterlace=true should reach the capture")

    test("/capture defaults deinterlace to false when the body omits it"):
        // Deliberately hand-written rather than round-tripped through CaptureRequest: the point is what
        // happens to a body from an older client that has never heard of this field.
        val body = s"""{"videoUrl":"${videoUrl.toExternalForm()}","elapsedTimeMillis":4567}"""
        assert(!deinterlaceSeenBy(body), "an absent flag must not turn deinterlacing on")

    test("/capture accepts deinterlace=false explicitly"):
        val req = CaptureRequest(videoUrl.toExternalForm(), 5678L, deinterlace = Some(false))
        assert(!deinterlaceSeenBy(req.stringify))
