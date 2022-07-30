/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.api

import scala.concurrent.Future
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import org.mbari.beholder.etc.circe.CirceCodecs.given

trait Endpoints:
  val log = System.getLogger(getClass.getName)

  def all: List[Endpoint[?, ?, ?, ?, ?]]
  def allImpl: List[ServerEndpoint[Any, Future]]

  val baseEndpoint = endpoint.errorOut(
    oneOf[ErrorMsg](
      oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
      oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[ServerError])),
      oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized])),
      oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[StatusMsg]))
    )
  )
