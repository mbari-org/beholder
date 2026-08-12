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

import java.util.concurrent.ThreadLocalRandom

sealed trait ErrorMsg:
    def message: String
    def responseCode: Int

/**
 * Just a simple class used to return a JSON error response
 * @param message
 *   the error message
 * @param responseCode
 *   the HTTP response code
 * @author
 *   Brian Schlining
 * @since 2021-11-23T11:00:00
 */
final case class StatusMsg(message: String, responseCode: Int)          extends ErrorMsg
final case class NotFound(message: String, responseCode: Int = 404)     extends ErrorMsg
final case class ServerError(message: String, responseCode: Int = 500)  extends ErrorMsg
final case class Unauthorized(message: String, responseCode: Int = 401) extends ErrorMsg

/**
 * The server is healthy but has no capacity for this request right now. Retrying later should work.
 *
 * @param retryAfter
 *   How many seconds to wait before retrying, sent as the `Retry-After` header as well as in the body. Prefer
 *   [[ServiceUnavailable.busy]] over this constructor — see there for why the value should not be a constant.
 */
final case class ServiceUnavailable(
    message: String,
    responseCode: Int = 503,
    retryAfter: Int = ServiceUnavailable.MinRetryAfterSeconds
) extends ErrorMsg

object ServiceUnavailable:

    /**
     * Bounds on the retry advice, in seconds. Short enough that a client is not parked long after capacity frees up,
     * long enough that several captures can finish first.
     */
    val MinRetryAfterSeconds: Int = 2
    val MaxRetryAfterSeconds: Int = 8

    /**
     * A 503 whose advice is spread across [[MinRetryAfterSeconds]]..[[MaxRetryAfterSeconds]].
     *
     * The jitter is the point of this method. A constant `Retry-After` tells every client shed in the same burst to
     * come back at the same instant, which reassembles the burst that caused the overload — and being in phase with
     * itself, it can then repeat indefinitely. Spreading the answers over a range breaks up the convoy.
     *
     * Deliberately not a random default on the constructor: that would make two otherwise equal `ServiceUnavailable`
     * values compare unequal, which quietly breaks case-class equality for every caller. Randomness stays visible at
     * the call site instead.
     */
    def busy(message: String): ServiceUnavailable =
        ServiceUnavailable(
            message,
            // nextInt's bound is exclusive, so +1 to make the top of the range reachable.
            retryAfter = ThreadLocalRandom.current().nextInt(MinRetryAfterSeconds, MaxRetryAfterSeconds + 1)
        )
