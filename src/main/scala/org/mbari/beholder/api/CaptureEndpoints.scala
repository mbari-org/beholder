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
import org.mbari.beholder.JpegCapture
import java.net.URL
import java.nio.file.Files

class CaptureEndpoints(jpegCapture: JpegCapture, apiKey: String)(using ec: ExecutionContext) extends Endpoints:

    val captureEndpoint =
        baseEndpoint
            .post
            .in("capture")
            .in(
                query[Option[Boolean]]("accurate").description(
                    "If true, capture at the exact time. (default: true). Otherwise seek to nearest keyframe before the elapsed time"
                )
            )
            .in(
                query[Option[Boolean]]("nokey").description(
                    "If true, skip non-key frames after the seek point. (default: false). Useful for fast processing, but potentially less accurate"
                )
            )
            .in(header[String]("X-Api-Key").description("Required key for access"))
            .in(jsonBody[CaptureRequest])
            .out(fileBody and header("Content-Type", "image/jpeg"))
            .name("capture")
            .description(
                "Capture a frame from a video at a given elapsed time or pull if from the cache if it exists"
            )
            .summary("Frame capture from a video")
            .tag("capture")

    val captureImpl: ServerEndpoint[Any, Future] =
        captureEndpoint
            .serverLogic((accurateOpt, nokeyOpt, xApiKey, captureRequest) =>
                Future {
                    for
                        _    <- if apiKey == xApiKey then Right(()) else Left(Unauthorized("Invalid X-Api-Key"))
                        url  <- captureRequest.url
                        jpeg <-
                            jpegCapture.capture(
                                url,
                                captureRequest.elapsedTime,
                                accurateOpt.getOrElse(true),
                                nokeyOpt.getOrElse(false)
                            )
                    yield jpeg.path.toFile
                }
            )

    def all: List[sttp.tapir.Endpoint[?, ?, ?, ?, ?]]                           = List(captureEndpoint)
    def allImpl: List[sttp.tapir.server.ServerEndpoint[Any, concurrent.Future]] = List(captureImpl)
