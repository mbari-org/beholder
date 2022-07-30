/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.api

import java.net.URL
import java.time.Duration
import scala.util.Try
import sttp.tapir.DecodeResult.Failure
import scala.util.Success

final case class CaptureRequest(videoUrl: String, elapsedTimeMillis: Long):
  lazy val elapsedTime: Duration      = Duration.ofMillis(elapsedTimeMillis)
  lazy val url: Either[ErrorMsg, URL] = Try(URL(videoUrl)) match
    case scala.util.Failure(_) => Left(NotFound(s"$videoUrl is not a valid URL"))
    case Success(u)            => Right(u)
