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

import scala.concurrent.ExecutionContext
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import org.mbari.beholder.etc.circe.CirceCodecs.given
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future
import org.mbari.beholder.{ImageCapture, ImageType}

class CaptureEndpoints(imageCapture: ImageCapture, apiKey: String)(using ec: ExecutionContext) extends Endpoints:

    // Shared query / header / body inputs reused across all three capture endpoints.
    private val accurateQuery =
        query[Option[Boolean]]("accurate").description(
            "If true, capture at the exact time. (default: true). Otherwise seek to nearest keyframe before the elapsed time"
        )
    private val nokeyQuery    =
        query[Option[Boolean]]("nokey").description(
            "If true, skip non-key frames after the seek point. (default: false). Useful for fast processing, but potentially less accurate"
        )
    private val apiKeyHeader  = header[String]("X-Api-Key").description("Required key for access")

    /**
     * Logic for /capture — imageType comes from the request body (defaults to JPEG when absent).
     * @param accurateOpt true if the capture should be accurate, false if it should be fast
     *                    (default: true)
     * @param nokeyOpt true if the capture should skip non-key frames, false if it should not
     *                    (default: false)
     * @param xApiKey the value of the X-Api-Key header in the request
     * @param captureRequest the body of the request, containing the URI, elapsed time, and optionally the image type
     */
    private def captureLogic(
        accurateOpt: Option[Boolean],
        nokeyOpt: Option[Boolean],
        xApiKey: String,
        captureRequest: CaptureRequest
    ): Future[Either[ErrorMsg, (java.io.File, String)]] =
        Future:
            for
                _   <- if apiKey == xApiKey then Right(()) else Left(Unauthorized("Invalid X-Api-Key"))
                url <- captureRequest.uri
                img <- imageCapture.capture(
                           url,
                           captureRequest.elapsedTime,
                           accurateOpt.getOrElse(true),
                           nokeyOpt.getOrElse(false),
                           captureRequest.imageType.getOrElse(ImageType.Jpeg)
                       )
            yield (img.path.toFile, img.imageType.mediaType)

    // Logic for /capture/jpg and /capture/png — imageType is fixed by the path.
    private def captureLogicHelper(imageType: ImageType)(
        accurateOpt: Option[Boolean],
        nokeyOpt: Option[Boolean],
        xApiKey: String,
        captureRequest: CaptureRequest
    ): Future[Either[ErrorMsg, java.io.File]] =
        val newCaptureRequest = captureRequest.copy(imageType = Some(imageType))
        captureLogic(accurateOpt, nokeyOpt, xApiKey, newCaptureRequest).map(_.map(_._1))

    val captureEndpoint =
        baseEndpoint
            .post
            .in("capture")
            .in(accurateQuery)
            .in(nokeyQuery)
            .in(apiKeyHeader)
            .in(jsonBody[CaptureRequest])
            .out(fileBody and header[String]("Content-Type"))
            .name("capture")
            .description(
                "Capture a frame from a video at a given elapsed time or pull it from the cache if it exists. " +
                    "Include imageType (jpg or png) in the request body to select the format; defaults to JPEG."
            )
            .summary("Frame capture from a video")
            .tag("capture")

    val captureJpgEndpoint =
        baseEndpoint
            .post
            .in("capture" / "jpg")
            .in(accurateQuery)
            .in(nokeyQuery)
            .in(apiKeyHeader)
            .in(jsonBody[CaptureRequest])
            .out(fileBody and header("Content-Type", "image/jpeg"))
            .name("capture-jpg")
            .description(
                "Capture a JPEG frame from a video at a given elapsed time or pull it from the cache if it exists"
            )
            .summary("Frame capture as JPEG from a video")
            .tag("capture")

    val capturePngEndpoint =
        baseEndpoint
            .post
            .in("capture" / "png")
            .in(accurateQuery)
            .in(nokeyQuery)
            .in(apiKeyHeader)
            .in(jsonBody[CaptureRequest])
            .out(fileBody and header("Content-Type", "image/png"))
            .name("capture-png")
            .description(
                "Capture a PNG frame from a video at a given elapsed time or pull it from the cache if it exists"
            )
            .summary("Frame capture as PNG from a video")
            .tag("capture")

    val captureImpl: ServerEndpoint[Any, Future] =
        captureEndpoint.serverLogic(captureLogic)

    val captureJpgImpl: ServerEndpoint[Any, Future] =
        captureJpgEndpoint.serverLogic(captureLogicHelper(ImageType.Jpeg))

    val capturePngImpl: ServerEndpoint[Any, Future] =
        capturePngEndpoint.serverLogic(captureLogicHelper(ImageType.Png))

    def all: List[sttp.tapir.Endpoint[?, ?, ?, ?, ?]] =
        List(captureEndpoint, captureJpgEndpoint, capturePngEndpoint)

    def allImpl: List[sttp.tapir.server.ServerEndpoint[Any, concurrent.Future]] =
        List(captureImpl, captureJpgImpl, capturePngImpl)
