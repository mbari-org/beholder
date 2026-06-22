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

package org.mbari.beholder.etc.circe

import io.circe.*
import io.circe.generic.semiauto.*
import scala.util.Try
import java.net.URL
import org.mbari.beholder.util.HexUtil
import org.mbari.beholder.api.{NotFound, ServerError, StatusMsg, Unauthorized}
import org.mbari.beholder.api.CaptureRequest
import org.mbari.beholder.api.HealthStatus
import org.mbari.beholder.ImageType
import java.net.URI

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
        .emapTry(str => Try(URI.create(str).toURL()))
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

    given Decoder[ImageType] = Decoder.decodeString.emap:
        case "jpg" | "jpeg" => Right(ImageType.Jpeg)
        case "png"          => Right(ImageType.Png)
        case other          => Left(s"Unknown image type: $other. Expected jpg, jpeg, or png")

    given Encoder[ImageType] = Encoder.encodeString.contramap(_.extension.stripPrefix("."))

    given Decoder[CaptureRequest] = deriveDecoder

//    given Decoder[CaptureRequest] = cursor =>
//        for
//            videoUrl          <- cursor.get[String]("videoUrl")
//            elapsedTimeMillis <- cursor.get[Long]("elapsedTimeMillis")
//            imageType         <- cursor.getOrElse[Option[ImageType]]("imageType")(None)
//        yield CaptureRequest(videoUrl, elapsedTimeMillis, imageType)
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
