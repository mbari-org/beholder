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

import org.mbari.beholder.TestUtil

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.mbari.beholder.etc.circe.CirceCodecs.{given, *}
import org.mbari.beholder.JpegCache
import org.mbari.beholder.JpegCapture
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.TapirStubInterpreter
import org.mbari.beholder.AppConfig

class CaptureEndpointsSuite extends munit.FunSuite:

  given ExecutionContextExecutor = ExecutionContext.global
  
  // -- Set up cache
  private val root = TestUtil.root
  Files.createDirectories(root)
  private val cache = JpegCache(root, 3, .3)
  private val capture = JpegCapture(cache)

  private val videoUrl = TestUtil.bigBuckBunny
  private val captureRequest = CaptureRequest(videoUrl.toExternalForm(), 1234L)
  private val captureEndpoint = CaptureEndpoints(capture, AppConfig.Api.Key)

  val backendStub: SttpBackend[Future, Any] = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
    .whenServerEndpoint(captureEndpoint.captureImpl)
    .thenRunLogic()
    .backend()

  test("/capture"):
    
    val response = basicRequest
      .post(uri"http://test.com/capture")
      .header("X-Api-Key", AppConfig.Api.Key)
      .body(captureRequest.stringify)
      .send(backendStub)

    val result = Await.result(response, Duration(10, TimeUnit.SECONDS))
    assertEquals(result.code.code, 200)
    

  test("/capture with invalid X-Api-Key"):
    val response = basicRequest
      .post(uri"http://test.com/capture")
      .header("X-Api-Key", "bad key")
      .body(captureRequest.stringify)
      .send(backendStub)

    val result = Await.result(response, Duration(10, TimeUnit.SECONDS))
    assertEquals(result.code.code, 401)

  test("/capture?accurate=false"):

    val captureRequest1 = CaptureRequest(videoUrl.toExternalForm(), 2345)
    
    val response = basicRequest
      .post(uri"http://test.com/capture?accurate=false")
      .header("X-Api-Key", AppConfig.Api.Key)
      .body(captureRequest1.stringify)
      .send(backendStub)

    val result = Await.result(response, Duration(10, TimeUnit.SECONDS))
    assertEquals(result.code.code, 200)
  
