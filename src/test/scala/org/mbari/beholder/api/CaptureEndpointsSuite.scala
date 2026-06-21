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

import org.mbari.beholder.{AppConfig, ImageCacheImpl, ImageCapture, TestUtil}

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.mbari.beholder.etc.circe.CirceCodecs.{*, given}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import sttp.client3.*
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.TapirStubInterpreter

class CaptureEndpointsSuite extends munit.FunSuite:

    given ExecutionContextExecutor = ExecutionContext.global

    private val root = TestUtil.root
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
        val result = await(basicRequest.post(uri"http://test.com/capture").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(captureStub))
        assertEquals(result.code.code, 200)

    test("/capture with invalid X-Api-Key"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(basicRequest.post(uri"http://test.com/capture").header("X-Api-Key", "bad key").body(req.stringify).send(captureStub))
        assertEquals(result.code.code, 401)

    test("/capture?accurate=false"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(basicRequest.post(uri"http://test.com/capture?accurate=false").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(captureStub))
        assertEquals(result.code.code, 200)

    test("/capture?nokey=true"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(basicRequest.post(uri"http://test.com/capture?nokey=true").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(captureStub))
        assertEquals(result.code.code, 200)

    // ---- /capture/jpg ----

    test("/capture/jpg"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(basicRequest.post(uri"http://test.com/capture/jpg").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(captureJpgStub))
        assertEquals(result.code.code, 200)

    test("/capture/jpg with invalid X-Api-Key"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(basicRequest.post(uri"http://test.com/capture/jpg").header("X-Api-Key", "bad key").body(req.stringify).send(captureJpgStub))
        assertEquals(result.code.code, 401)

    test("/capture/jpg?accurate=false"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(basicRequest.post(uri"http://test.com/capture/jpg?accurate=false").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(captureJpgStub))
        assertEquals(result.code.code, 200)

    test("/capture/jpg?nokey=true"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(basicRequest.post(uri"http://test.com/capture/jpg?nokey=true").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(captureJpgStub))
        assertEquals(result.code.code, 200)

    // ---- /capture/png ----

    test("/capture/png"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(basicRequest.post(uri"http://test.com/capture/png").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(capturePngStub))
        assertEquals(result.code.code, 200)

    test("/capture/png with invalid X-Api-Key"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 1234L)
        val result = await(basicRequest.post(uri"http://test.com/capture/png").header("X-Api-Key", "bad key").body(req.stringify).send(capturePngStub))
        assertEquals(result.code.code, 401)

    test("/capture/png?accurate=false"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(basicRequest.post(uri"http://test.com/capture/png?accurate=false").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(capturePngStub))
        assertEquals(result.code.code, 200)

    test("/capture/png?nokey=true"):
        val req    = CaptureRequest(videoUrl.toExternalForm(), 2345L)
        val result = await(basicRequest.post(uri"http://test.com/capture/png?nokey=true").header("X-Api-Key", AppConfig.Api.Key).body(req.stringify).send(capturePngStub))
        assertEquals(result.code.code, 200)
