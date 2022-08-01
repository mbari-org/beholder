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

package org.mbari.beholder

import io.vertx.core.Vertx
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.Router
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import org.mbari.beholder.api.{CaptureEndpoints, SwaggerEndpoints}
import org.mbari.beholder.api.HealthEndpoints
import org.mbari.beholder.etc.jdk.Logging.given
import picocli.CommandLine
import picocli.CommandLine.{Command, Option => Opt, Parameters}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import sttp.tapir.server.vertx.VertxFutureServerInterpreter
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.*

@Command(
  description = Array("Start the server"),
  name = "main",
  mixinStandardHelpOptions = true,
  version = Array("0.0.1")
)
class MainRunner extends Callable[Int]:

  @Opt(
    names = Array("-p", "--port"),
    description = Array("The port of the server. default: ${DEFAULT-VALUE}")
  )
  private var port: Int = AppConfig.Http.Port

  @Opt(
    names = Array("-c", "--cachesize"),
    description = Array("The maximum allowed size in MB of the image cache")
  )
  private var cacheSizeMB = AppConfig.Cache.sizeMb

  @Opt(
    names = Array("-f", "--freepct"),
    description = Array("The percent of max cache to free when it's full. value between 0 and 1")
  )
  private var freePct = AppConfig.Cache.freePct

  @Opt(
    names = Array("-k", "--apikey"),
    description = Array("API Key")
  )
  private var apiKey = AppConfig.Api.Key

  @Parameters(
    paramLabel = "<rootDirectory>",
    description = Array("The location of the image cache")
  )
  private var cacheRoot: Path = _

  override def call(): Int =
    Main.run(port, cacheRoot, cacheSizeMB, freePct, apiKey)
    0

object Main:

  private val log = System.getLogger(getClass.getName())

  def main(args: Array[String]): Unit =
    val s = """
      | :::====  :::===== :::  === :::====  :::      :::====  :::===== :::==== 
      | :::  === :::      :::  === :::  === :::      :::  === :::      :::  ===
      | =======  ======   ======== ===  === ===      ===  === ======   ======= 
      | ===  === ===      ===  === ===  === ===      ===  === ===      === === 
      | =======  ======== ===  ===  ======  ======== =======  ======== ===  ===""".stripMargin
    println(s)
    new CommandLine(new MainRunner()).execute(args: _*)

  def run(
      port: Int,
      cacheRoot: Path,
      cacheSizeMb: Int = AppConfig.Cache.sizeMb,
      freePct: Double = AppConfig.Cache.freePct,
      apiKey: String = AppConfig.Api.Key
  ): Unit =
    log.atInfo.log(s"Starting up ${AppConfig.Name} v${AppConfig.Version} on port $port")

    given executionContext: ExecutionContextExecutor = ExecutionContext.global

    // -- Vert.x server
    val vertx  = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    // Add CORS
    val corsHandler = CorsHandler.create("*")
    router.route().handler(corsHandler)

    // create cache if needed
    if (!Files.exists(cacheRoot))
      log.atInfo.log(() => s"Creating cache directory: $cacheRoot")
      Files.createDirectories(cacheRoot)

    val jpegCache        = JpegCache(cacheRoot, cacheSizeMb, freePct)
    val jpegCapture      = JpegCapture(jpegCache)
    val captureEndpoints = CaptureEndpoints(jpegCapture, apiKey)
    val healthEndpoints  = HealthEndpoints()
    val swaggerEndpoints = SwaggerEndpoints(captureEndpoints, healthEndpoints)
    val allEndpointImpls =
      captureEndpoints.allImpl ++ healthEndpoints.allImpl ++ swaggerEndpoints.allImpl

    // Add Tapir endpoints
    for endpoint <- allEndpointImpls do
      val attach = VertxFutureServerInterpreter().route(endpoint)
      attach(router)

    Await.result(server.requestHandler(router).listen(port).asScala, Duration.Inf)
