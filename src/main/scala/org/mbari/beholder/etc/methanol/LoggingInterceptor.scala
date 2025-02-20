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

package org.mbari.beholder.etc.methanol

import com.github.mizosoft.methanol.Methanol
import com.github.mizosoft.methanol.Methanol.Interceptor.Chain

import java.net.http.{HttpHeaders, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

object LoggingInterceptor extends Methanol.Interceptor:

    private val log = System.getLogger(getClass.getName)

    override def intercept[T](request: HttpRequest, chain: Chain[T]): HttpResponse[T] =
        logRequest(request)
        toLoggingChain(request, chain).forward(request)

    override def interceptAsync[T](
        request: HttpRequest,
        chain: Chain[T]
    ): CompletableFuture[HttpResponse[T]] =
        logRequest(request)
        toLoggingChain(request, chain).forwardAsync(request)

    private def logRequest(request: HttpRequest): Unit =
        log.log(
            System.Logger.Level.DEBUG,
            () => s""" Sent >>>
        |${request.method()} ${request.uri()}
        |${headersToString(request.headers())}""".stripMargin.trim()
        )

    private def toLoggingChain[T](request: HttpRequest, chain: Chain[T]): Chain[T] =

        val sentAt = Instant.now()
        // format: off
        chain.withBodyHandler(responseInfo =>
            log.log(System.Logger.Level.DEBUG, () =>
                s""" Received <<< ${request.method()} ${request.uri()} in ${Duration.between(sentAt, Instant.now()).toMillis()}ms
                |${responseInfo.statusCode()}
                |${headersToString(responseInfo.headers())}""".stripMargin.trim()
                )
            chain.bodyHandler().apply(responseInfo)
        )
        // format: on

    private def headersToString(headers: HttpHeaders): String =
        headers
            .map()
            .entrySet()
            .stream()
            .map(e => s"${e.getKey()}: ${String.join(", ", e.getValue())}")
            .collect(Collectors.joining(System.lineSeparator()))
            .trim()
