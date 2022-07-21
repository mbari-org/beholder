/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder.etc.jdk

import java.time.Duration

class DurationUtilSuite extends munit.FunSuite:

  val h = 6
  val m = 5
  val ms = math.round(4.321 * 1000)
  val d = Duration.ofMillis(ms).plusHours(h).plusMinutes(m)

  test("toHMS") {
    val hms = DurationUtil.toHMS(d)
    assertEquals(hms, "06:05:04.321")
  }

  test("fromHMS") {
    val t = DurationUtil.fromHMS(DurationUtil.toHMS(d))
    assertEquals(t, d)
  }