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

class ServiceUnavailableSuite extends munit.FunSuite:

    private val bounds  = ServiceUnavailable.MinRetryAfterSeconds to ServiceUnavailable.MaxRetryAfterSeconds
    private val samples = 200

    private def adviceFrom(n: Int): Seq[Int] = (1 to n).map(_ => ServiceUnavailable.busy("no capacity").retryAfter)

    test("busy advises a retry within its bounds"):
        val outOfRange = adviceFrom(samples).filterNot(bounds.contains)
        assertEquals(outOfRange, Seq.empty[Int], s"advice outside $bounds")

    /**
     * The reason `busy` exists. A constant Retry-After sends every client shed by one burst back at the same instant,
     * which rebuilds the burst — in phase with itself, so it can repeat indefinitely.
     *
     * 200 draws over a range this small covers all of it with overwhelming probability, so asserting full coverage is a
     * real check on the spread rather than a coin flip.
     */
    test("busy spreads its advice across the range, so a shed burst does not retry in lockstep"):
        assertEquals(adviceFrom(samples).toSet, bounds.toSet)

    /**
     * Why the jitter lives in `busy` rather than in a default argument on the constructor: a random default would make
     * two otherwise identical values compare unequal, breaking case-class equality for everyone.
     */
    test("the plain constructor stays deterministic, so equality still holds"):
        assertEquals(ServiceUnavailable("no capacity"), ServiceUnavailable("no capacity"))
