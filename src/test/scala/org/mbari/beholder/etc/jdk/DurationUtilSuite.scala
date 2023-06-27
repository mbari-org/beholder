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

package org.mbari.beholder.etc.jdk

import java.time.Duration

class DurationUtilSuite extends munit.FunSuite:

  val h = 6
  val m = 5
  val ms = math.round(4.321 * 1000)
  val d = Duration.ofMillis(ms).plusHours(h).plusMinutes(m)

  test("toHMS"):
    val hms = DurationUtil.toHMS(d)
    assertEquals(hms, "06:05:04.321")

  test("fromHMS") {
    val t = DurationUtil.fromHMS(DurationUtil.toHMS(d))
    assertEquals(t, d)
  }