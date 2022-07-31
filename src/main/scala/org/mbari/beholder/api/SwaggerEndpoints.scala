/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.api

import sttp.tapir.server.ServerEndpoint
import scala.concurrent.Future
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import org.mbari.beholder.AppConfig

case class SwaggerEndpoints(captureEndpoints: CaptureEndpoints, healthEndpoints: HealthEndpoints):

  val allImpl: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter()
      .fromEndpoints[Future](
        List(
          captureEndpoints.captureEndpoint,
          healthEndpoints.defaultEndpoint
        ),
        AppConfig.Name,
        AppConfig.Version
      )
