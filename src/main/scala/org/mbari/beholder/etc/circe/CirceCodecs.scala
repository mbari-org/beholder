/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.etc.circe

import io.circe._
import io.circe.generic.semiauto._
import scala.util.Try
import java.net.URL
import org.mbari.beholder.util.HexUtil
import org.mbari.beholder.api.{NotFound, ServerError, StatusMsg, Unauthorized}
import org.mbari.beholder.api.CaptureRequest
import scala.CanEqual.derived
import org.mbari.beholder.api.HealthStatus

/**
 * Contains all the circe codecs needed as givens
 */
object CirceCodecs:

  given byteArrayEncoder: Encoder[Array[Byte]] = new Encoder[Array[Byte]]:
    final def apply(xs: Array[Byte]): Json =
      Json.fromString(HexUtil.toHex(xs))
  given byteArrayDecoder: Decoder[Array[Byte]] = Decoder
    .decodeString
    .emapTry(str => Try(HexUtil.fromHex(str)))

  given urlDecoder: Decoder[URL] = Decoder
    .decodeString
    .emapTry(str => Try(new URL(str)))
  given urlEncoder: Encoder[URL] = Encoder
    .encodeString
    .contramap(_.toString)

  given Decoder[StatusMsg] = deriveDecoder
  given Encoder[StatusMsg] = deriveEncoder

  given Decoder[NotFound] = deriveDecoder
  given Encoder[NotFound] = deriveEncoder

  given Decoder[ServerError] = deriveDecoder
  given Encoder[ServerError] = deriveEncoder

  given Decoder[Unauthorized] = deriveDecoder
  given Encoder[Unauthorized] = deriveEncoder

  given Decoder[CaptureRequest] = deriveDecoder
  given Encoder[CaptureRequest] = deriveEncoder

  given Decoder[HealthStatus] = deriveDecoder
  given Encoder[HealthStatus] = deriveEncoder

  private val printer = Printer.noSpaces.copy(dropNullValues = true)

  /**
   * Convert a circe Json object to a JSON string
   * @param value
   *   Any value with an implicit circe coder in scope
   */
  extension (json: Json) def stringify: String = printer.print(json)

  /**
   * Convert an object to a JSON string
   * @param value
   *   Any value with an implicit circe coder in scope
   */
  extension [T: Encoder](value: T) def stringify: String = Encoder[T].apply(value).stringify
