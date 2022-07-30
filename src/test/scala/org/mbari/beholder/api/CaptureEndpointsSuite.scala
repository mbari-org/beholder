/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
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


  test("/capture") {
    val videoUrl = TestUtil.bigBuckBunny
    val captureRequest = CaptureRequest(videoUrl.toExternalForm(), 1234L)
    val captureEndpoint = CaptureEndpoints(capture, AppConfig.Api.Key)

    val backendStub: SttpBackend[Future, Any] = TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
      .whenServerEndpoint(captureEndpoint.captureImpl)
      .thenRunLogic()
      .backend()

    val response = basicRequest
      .post(uri"http://test.com/capture")
      .header("X-Api-Key", AppConfig.Api.Key)
      .body(captureRequest.stringify)
      .send(backendStub)

    val result = Await.result(response, Duration(10, TimeUnit.SECONDS))
    assertEquals(result.code.code, 200)

    
  }
  
