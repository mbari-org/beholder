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

import scala.concurrent.Future
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.ExecutionContext
import org.mbari.beholder.etc.circe.CirceCodecs.given

class HealthEndpoints(using ec: ExecutionContext) extends Endpoints:

    val defaultEndpoint: PublicEndpoint[Unit, ErrorMsg, HealthStatus, Any] =
        baseEndpoint
            .get
            .in("health")
            .out(jsonBody[HealthStatus])
            .name("beholderHealth")
            .description("Get the health status of the server")
            .tag("health")
    val defaultImpl: ServerEndpoint[Any, Future]                           =
        defaultEndpoint.serverLogic(Unit => Future(Right(HealthStatus.default)))

    def all: List[sttp.tapir.Endpoint[?, ?, ?, ?, ?]]                           = List(defaultEndpoint)
    def allImpl: List[sttp.tapir.server.ServerEndpoint[Any, concurrent.Future]] = List(defaultImpl)
