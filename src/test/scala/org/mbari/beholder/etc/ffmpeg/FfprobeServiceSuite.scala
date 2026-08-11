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

import java.net.URI
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.mbari.beholder.TestUtil

class FfprobeServiceSuite extends munit.FunSuite:

    private val uri  = URI.create("file:///does/not/need/to/exist.mp4")
    private val size = VideoSize(1920, 1080)

    /** Records how many times the underlying probe actually ran. */
    private class CountingFfprobe(result: Option[VideoSize], delayMillis: Long = 0) extends Ffprobe:
        val calls = AtomicInteger(0)

        override def videoSize(videoUri: URI): Option[VideoSize] =
            calls.incrementAndGet()
            if delayMillis > 0 then Thread.sleep(delayMillis)
            result

    test("videoSize returns what the delegate found"):
        val probe   = CountingFfprobe(Some(size))
        val service = FfprobeService(probe)
        assertEquals(service.videoSize(uri), Some(size))
        assertEquals(probe.calls.get(), 1)

    test("repeated lookups are served from the cache"):
        val probe   = CountingFfprobe(Some(size))
        val service = FfprobeService(probe)
        for _ <- 1 to 10 do assertEquals(service.videoSize(uri), Some(size))
        assertEquals(probe.calls.get(), 1, "delegate should only have been asked once")

    test("distinct videos are cached separately"):
        val probe   = CountingFfprobe(Some(size))
        val service = FfprobeService(probe)
        service.videoSize(URI.create("file:///a.mp4"))
        service.videoSize(URI.create("file:///b.mp4"))
        service.videoSize(URI.create("file:///a.mp4"))
        assertEquals(probe.calls.get(), 2)
        assertEquals(service.estimatedSize, 2L)

    test("a failed probe is not cached, so it gets retried"):
        val probe   = CountingFfprobe(None)
        val service = FfprobeService(probe)
        assertEquals(service.videoSize(uri), None)
        assertEquals(service.videoSize(uri), None)
        assertEquals(probe.calls.get(), 2, "a transient failure must not be remembered")
        assertEquals(service.estimatedSize, 0L)

    test("invalidateAll forces a re-probe"):
        val probe   = CountingFfprobe(Some(size))
        val service = FfprobeService(probe)
        service.videoSize(uri)
        service.invalidateAll()
        service.videoSize(uri)
        assertEquals(probe.calls.get(), 2)

    test("concurrent lookups of the same video share one probe"):
        val threads = 16
        val probe   = CountingFfprobe(Some(size), delayMillis = 50)
        val service = FfprobeService(probe)
        val ready   = CountDownLatch(threads)
        val go      = CountDownLatch(1)
        val pool    = Executors.newFixedThreadPool(threads)
        try
            val futures = (1 to threads).map: _ =>
                pool.submit: () =>
                    ready.countDown()
                    go.await()
                    service.videoSize(uri)
            ready.await(10, TimeUnit.SECONDS)
            go.countDown()
            futures.foreach(f => assertEquals(f.get(10, TimeUnit.SECONDS), Some(size)))
        finally pool.shutdownNow()
        assertEquals(probe.calls.get(), 1, "concurrent callers should not each shell out to ffprobe")

    test("the real ffprobe reports the test video's declared size"):
        val service = FfprobeService()
        assertEquals(service.videoSize(TestUtil.bigBuckBunny.toURI), Some(VideoSize(1920, 1080)))
