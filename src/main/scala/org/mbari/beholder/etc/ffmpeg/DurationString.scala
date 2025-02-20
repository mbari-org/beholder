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

package org.mbari.beholder.etc.ffmpeg

import java.time.Duration
import org.mbari.beholder.etc.jdk.DurationUtil
import scala.util.Try

/**
 * opaque wrapper around duration strings formatted as hh:mm:ss.sss. This is the elapsedTime format used by FFMpeg
 *
 * Added so we don't have raw string types in Cache requests.
 */
object DurationString:

    opaque type DurationString = String
    object DurationString:
        def apply(s: String): DurationString             = s
        def apply(d: Duration): DurationString           = DurationUtil.toHMS(d)
        def unapply(s: DurationString): Option[Duration] = Try(DurationUtil.fromHMS(s)).toOption
