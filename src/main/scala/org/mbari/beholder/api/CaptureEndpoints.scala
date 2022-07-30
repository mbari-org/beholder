/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.api

import scala.concurrent.ExecutionContext
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import org.mbari.beholder.etc.circe.CirceCodecs.given
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future
import org.mbari.beholder.JpegCapture
import java.net.URL
import java.nio.file.Files

class CaptureEndpoints(jpegCapture: JpegCapture, apiKey: String)(using ec: ExecutionContext)
    extends Endpoints:

  val captureEndpoint =
    baseEndpoint
      .post
      .in("capture")
      .in(header[String]("X-Api-Key").description("Required key for access"))
      .in(jsonBody[CaptureRequest])
      .out(fileBody and header("Content-Type", "image/jpeg"))
      .name("capture")
      .description("Capture a frame from a video at a given elapsed time")
      .summary("Frame capture from a video")
      .tag("capture")

  val captureImpl: ServerEndpoint[Any, Future] =
    captureEndpoint
      .serverLogic((xApiKey, captureRequest) =>
        Future {
          for
            _    <- if (apiKey == xApiKey) Right(()) else Left(Unauthorized("Invalid X-Api-Key"))
            url  <- captureRequest.url
            jpeg <- jpegCapture.capture(url, captureRequest.elapsedTime)
          yield jpeg.path.toFile
        }
      )

  def all: List[sttp.tapir.Endpoint[?, ?, ?, ?, ?]]                           = List(captureEndpoint)
  def allImpl: List[sttp.tapir.server.ServerEndpoint[Any, concurrent.Future]] = List(captureImpl)
