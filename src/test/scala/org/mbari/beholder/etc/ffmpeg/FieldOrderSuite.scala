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

class FieldOrderSuite extends munit.FunSuite:

    test("every interlaced field order ffprobe can report is recognized as interlaced"):
        for token <- Seq("tt", "bb", "tb", "bt") do
            val parsed = FieldOrder.parse(token)
            assertNotEquals(parsed, FieldOrder.Unknown, s"'$token' should parse to a known field order")
            assert(parsed.isInterlaced, s"'$token' is an interlaced field order")

    test("progressive is parsed and is not interlaced"):
        assertEquals(FieldOrder.parse("progressive"), FieldOrder.Progressive)
        assert(!FieldOrder.Progressive.isInterlaced)

    test("parsing tolerates the whitespace and case that come back from a CSV"):
        assertEquals(FieldOrder.parse("  TB \n"), FieldOrder.Tb)
        assertEquals(FieldOrder.parse("Progressive"), FieldOrder.Progressive)

    /**
     * Deinterlacing a progressive frame visibly softens it, so when we cannot tell, leaving the frame alone is the
     * cheaper mistake.
     */
    test("anything unrecognized is Unknown and is left alone"):
        for token <- Seq("unknown", "", "   ", "sideways", "tt bb") do
            assertEquals(FieldOrder.parse(token), FieldOrder.Unknown, s"'$token' should be Unknown")
        assert(!FieldOrder.Unknown.isInterlaced)

    test("VideoInfo defers to its field order and still exposes the size"):
        assert(VideoInfo(1920, 1080, FieldOrder.Tb).isInterlaced)
        assert(!VideoInfo(1920, 1080, FieldOrder.Progressive).isInterlaced)
        assertEquals(VideoInfo(1920, 1080, FieldOrder.Tb).size, VideoSize(1920, 1080))

    test("a VideoInfo with no stated field order is not interlaced"):
        assert(!VideoInfo(1920, 1080).isInterlaced)
