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

object DurationUtil:

    /**
     * Format a duration as hh:mm:ss.sss
     */
    def toHMS(duration: Duration): String =
        val h  = duration.toHoursPart
        val m  = duration.toMinutesPart
        val s  = duration.toSecondsPart
        val ms = duration.toMillisPart
        f"$h%02d:$m%02d:$s%02d.$ms%03d"

    /**
     * Parse a duration from a string of "hh:mm:ss.sss"
     */
    def fromHMS(hms: String): Duration =
        val parts = hms.split(":")
        val h     = parts(0).toInt
        val m     = parts(1).toInt
        // TODO should we round or truncate/floor?
        val ms    = math.round(parts(2).toDouble * 1000L)
        Duration
            .ofMillis(ms)
            .plusHours(h)
            .plusMinutes(m)
