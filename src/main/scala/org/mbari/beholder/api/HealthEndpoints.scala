/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
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
