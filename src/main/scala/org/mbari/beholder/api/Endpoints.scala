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

import org.mbari.beholder.etc.circe.CirceCodecs.given
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

/**
 * Base class for endpoints in the app
 */
trait Endpoints:
    val log = System.getLogger(getClass.getName)

    def all: List[Endpoint[?, ?, ?, ?, ?]]
    def allImpl: List[ServerEndpoint[Any, Future]]

    /**
     * 503 is the one error worth acting on rather than just reporting, so it carries `Retry-After` alongside the JSON.
     * The advice is already in the body as prose, but no client parses prose; the header is what a well-behaved one
     * backs off on.
     *
     * The header and the body field are two views of the same number, hence the mapping: encoding projects `retryAfter`
     * into both, and decoding takes the body and ignores the header, which is redundant there.
     */
    private val serviceUnavailableOut: EndpointOutput[ServiceUnavailable] =
        statusCode(StatusCode.ServiceUnavailable)
            .and(header[Int]("Retry-After"))
            .and(jsonBody[ServiceUnavailable])
            .map { case (_, body) => body }(body => (body.retryAfter, body))

    val baseEndpoint = endpoint.errorOut(
        oneOf[ErrorMsg](
            oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
            oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[ServerError])),
            oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized])),
            oneOfVariant(serviceUnavailableOut),
            oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[StatusMsg]))
        )
    )
