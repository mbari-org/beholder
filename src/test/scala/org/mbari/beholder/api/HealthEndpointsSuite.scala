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

import org.mbari.beholder.LoggingFunSuite
import java.util.concurrent.{RejectedExecutionException, TimeUnit}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import sttp.client3.*
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.TapirStubInterpreter

class HealthEndpointsSuite extends LoggingFunSuite:

    private def stub(endpoints: HealthEndpoints): SttpBackend[Future, Any] =
        TapirStubInterpreter(SttpBackendStub.asynchronousFuture)
            .whenServerEndpoint(endpoints.defaultImpl)
            .thenRunLogic()
            .backend()

    private def get(backend: SttpBackend[Future, Any]) =
        Await.result(
            basicRequest.get(uri"http://test.com/health").send(backend),
            Duration(10, TimeUnit.SECONDS)
        )

    test("/health reports the server status"):
        given ExecutionContext = ExecutionContext.global
        val result             = get(stub(HealthEndpoints()))
        assertEquals(result.code.code, 200)
        assert(
            result.body.exists(_.contains("beholder")),
            s"Expected the application name in the body, got ${result.body}"
        )

    /**
     * A health check exists to answer while the service is under strain, so it must not need a thread from anywhere to
     * do it. This ExecutionContext refuses every task: if the endpoint schedules its work rather than answering inline,
     * the request cannot succeed.
     */
    test("/health answers without scheduling any work on an ExecutionContext"):
        given ExecutionContext = new ExecutionContext:
            def execute(runnable: Runnable): Unit     =
                throw new RejectedExecutionException("health should not need to schedule anything")
            def reportFailure(cause: Throwable): Unit = ()

        val result = get(stub(HealthEndpoints()))
        assertEquals(result.code.code, 200)
