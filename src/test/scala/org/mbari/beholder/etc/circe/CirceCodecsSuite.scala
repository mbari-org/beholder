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

import io.circe.parser.decode
import org.mbari.beholder.ImageType
import org.mbari.beholder.etc.circe.CirceCodecs.{*, given}

class CirceCodecsSuite extends munit.FunSuite:

    // ---- ImageType decoder ----

    test("decode jpg"):
        assertEquals(decode[ImageType]("\"jpg\""), Right(ImageType.Jpeg))

    test("decode jpeg"):
        assertEquals(decode[ImageType]("\"jpeg\""), Right(ImageType.Jpeg))

    test("decode png"):
        assertEquals(decode[ImageType]("\"png\""), Right(ImageType.Png))

    test("decode unknown returns Left"):
        assert(decode[ImageType]("\"bmp\"").isLeft)

    // ---- ImageType encoder ----

    test("encode Jpeg"):
        assertEquals(ImageType.Jpeg.stringify, "\"jpg\"")

    test("encode Png"):
        assertEquals(ImageType.Png.stringify, "\"png\"")

    // ---- Round-trip ----

    test("round-trip Jpeg"):
        assertEquals(decode[ImageType](ImageType.Jpeg.stringify), Right(ImageType.Jpeg))

    test("round-trip Png"):
        assertEquals(decode[ImageType](ImageType.Png.stringify), Right(ImageType.Png))
