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
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.mbari.beholder.TestUtil

class FfprobeServiceSuite extends LoggingFunSuite:

    private val uri  = URI.create("file:///does/not/need/to/exist.mp4")
    private val info = VideoInfo(1920, 1080, FieldOrder.Tb)

    /** Records how many times the underlying probe actually ran. */
    private class CountingFfprobe(result: Option[VideoInfo], delayMillis: Long = 0) extends Ffprobe:
        val calls = AtomicInteger(0)

        override def probe(videoUri: URI): Option[VideoInfo] =
            calls.incrementAndGet()
            if delayMillis > 0 then Thread.sleep(delayMillis)
            result

    test("probe returns what the delegate found"):
        val probe   = CountingFfprobe(Some(info))
        val service = FfprobeService(probe)
        assertEquals(service.probe(uri), Some(info))
        assertEquals(probe.calls.get(), 1)

    test("repeated lookups are served from the cache"):
        val probe   = CountingFfprobe(Some(info))
        val service = FfprobeService(probe)
        for _ <- 1 to 10 do assertEquals(service.probe(uri), Some(info))
        assertEquals(probe.calls.get(), 1, "delegate should only have been asked once")

    /**
     * The size and the field order come from one probe, so asking for both must not cost two. This is what makes it
     * affordable for `ImageCapture` to ask about interlacing and `FfmpegUtil` to ask about the crop.
     */
    test("the size and the field order share a single probe"):
        val probe   = CountingFfprobe(Some(info))
        val service = FfprobeService(probe)
        assertEquals(service.videoSize(uri), Some(VideoSize(1920, 1080)))
        assert(service.isInterlaced(uri))
        assertEquals(service.probe(uri), Some(info))
        assertEquals(probe.calls.get(), 1, "three questions about one video should be one shell-out")

    test("distinct videos are cached separately"):
        val probe   = CountingFfprobe(Some(info))
        val service = FfprobeService(probe)
        service.probe(URI.create("file:///a.mp4"))
        service.probe(URI.create("file:///b.mp4"))
        service.probe(URI.create("file:///a.mp4"))
        assertEquals(probe.calls.get(), 2)
        assertEquals(service.estimatedSize, 2L)

    test("a failed probe is not cached, so it gets retried"):
        val probe   = CountingFfprobe(None)
        val service = FfprobeService(probe)
        assertEquals(service.probe(uri), None)
        assertEquals(service.probe(uri), None)
        assertEquals(probe.calls.get(), 2, "a transient failure must not be remembered")
        assertEquals(service.estimatedSize, 0L)

    test("a video that cannot be probed is not reported as interlaced"):
        val service = FfprobeService(CountingFfprobe(None))
        assert(!service.isInterlaced(uri))

    test("invalidateAll forces a re-probe"):
        val probe   = CountingFfprobe(Some(info))
        val service = FfprobeService(probe)
        service.probe(uri)
        service.invalidateAll()
        service.probe(uri)
        assertEquals(probe.calls.get(), 2)

    test("concurrent lookups of the same video share one probe"):
        val threads = 16
        val probe   = CountingFfprobe(Some(info), delayMillis = 50)
        val service = FfprobeService(probe)
        val ready   = CountDownLatch(threads)
        val go      = CountDownLatch(1)
        val pool    = Executors.newFixedThreadPool(threads)
        try
            val futures = (1 to threads).map: _ =>
                pool.submit: () =>
                    ready.countDown()
                    go.await()
                    service.probe(uri)
            ready.await(10, TimeUnit.SECONDS)
            go.countDown()
            futures.foreach(f => assertEquals(f.get(10, TimeUnit.SECONDS), Some(info)))
        finally pool.shutdownNow()
        assertEquals(probe.calls.get(), 1, "concurrent callers should not each shell out to ffprobe")

    test("the real ffprobe reports the test video's declared size and field order"):
        val service = FfprobeService()
        assertEquals(service.videoSize(TestUtil.bigBuckBunny.toURI), Some(VideoSize(1920, 1080)))
        assertEquals(service.probe(TestUtil.bigBuckBunny.toURI).map(_.fieldOrder), Some(FieldOrder.Progressive))
