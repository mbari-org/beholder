/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder.etc.ffmpeg

import java.time.Duration
import org.mbari.beholder.etc.jdk.DurationUtil
import scala.util.Try

/**
 * opaque wrapper around duration strings formatted as hh:mm:ss.sss. This is the 
 * elapsedTime format used by FFMpeg
 * 
 * Added so we don't have raw string types in Cache requests.
 */
object DurationString:

  opaque type DurationString = String
  object DurationString:
    def apply(s: String): DurationString             = s
    def apply(d: Duration): DurationString           = DurationUtil.toHMS(d)
    def unapply(s: DurationString): Option[Duration] = Try(DurationUtil.fromHMS(s)).toOption
