/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder

import java.util.concurrent.Callable
import picocli.CommandLine
import picocli.CommandLine.{Command, Option => Opt, Parameters}
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import org.mbari.beholder.etc.jdk.Logging.given
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import scala.concurrent.Await
import scala.concurrent.duration.Duration
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

  override def call(): Int =
    Main.run(port)
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

  def run(port: Int): Unit =
    log.atInfo.log(s"Starting up ${AppConfig.Name} v${AppConfig.Version} on port $port")

    given executionContext: ExecutionContextExecutor = ExecutionContext.global

    // -- Vert.x server
    val vertx  = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    // Add CORS
    val corsHandler = CorsHandler.create("*")
    router.route().handler(corsHandler)

    // Add Tapir endpoints
    // for endpoint <- allEndpointImpls do
    //   val attach = VertxFutureServerInterpreter().route(endpoint)
    //   attach(router)

    Await.result(server.requestHandler(router).listen(port).asScala, Duration.Inf)
