/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder.etc.jdk

import java.time.Duration

object DurationUtil:

  def toHMS(duration: Duration): String =
    val h  = duration.toHoursPart
    val m  = duration.toMinutesPart
    val s  = duration.toSecondsPart
    val ms = duration.toMillisPart
    f"$h%02d:$m%02d:$s%02d.$ms%03d"

  def fromHMS(hms: String): Duration =
    val parts = hms.split(":")
    val h     = parts(0).toInt
    val m     = parts(1).toInt
    val ms    = math.round(parts(2).toDouble * 1000L)
    Duration
      .ofMillis(ms)
      .plusHours(h)
      .plusMinutes(m)
