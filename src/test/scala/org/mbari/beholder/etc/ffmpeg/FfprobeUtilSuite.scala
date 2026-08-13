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

import org.mbari.beholder.LoggingFunSuite
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import org.mbari.beholder.TestUtil

class FfprobeUtilSuite extends LoggingFunSuite:

    test("videoSize reports the size the video declares"):
        assertEquals(FfprobeUtil.videoSize(TestUtil.bigBuckBunny.toURI), Some(VideoSize(1920, 1080)))

    test("probe reports the size and the field order together"):
        assertEquals(
            FfprobeUtil.probe(TestUtil.bigBuckBunny.toURI),
            Some(VideoInfo(1920, 1080, FieldOrder.Progressive))
        )

    test("a progressive video is not reported as interlaced"):
        assert(!FfprobeUtil.isInterlaced(TestUtil.bigBuckBunny.toURI))

    test("videoSize is None for a video that does not exist"):
        assertEquals(FfprobeUtil.videoSize(URI.create("file:///no/such/video.mp4")), None)

    /** An unprobeable video must not be deinterlaced on spec. */
    test("a video that cannot be probed is not reported as interlaced"):
        assert(!FfprobeUtil.isInterlaced(URI.create("file:///no/such/video.mp4")))

    test("videoSize is None for a file that exists but is not a video"):
        val notAVideo = Files.createTempFile("beholder_not_a_video_", ".txt")
        try
            Files.writeString(notAVideo, "I am definitely not an MP4")
            assertEquals(FfprobeUtil.videoSize(notAVideo.toUri), None)
        finally Files.deleteIfExists(notAVideo)

    /**
     * A probe runs on the same pool as the capture it precedes, so an unreachable video must not be able to pin that
     * thread either. ffprobe cannot open and read a stream in a millisecond, so this always trips the timeout.
     */
    test("videoSize is None when ffprobe exceeds its timeout"):
        val impatient = FfprobeUtil(Duration.ofMillis(1))
        assertEquals(impatient.videoSize(TestUtil.bigBuckBunny.toURI), None)
