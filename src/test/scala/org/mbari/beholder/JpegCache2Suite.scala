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

package org.mbari.beholder

import java.nio.file.Files
import java.time.Duration
import org.junit.Assert.*

class JpegCache2Suite extends munit.FunSuite:

    val root     = TestUtil.root
    Files.createDirectories(root)
    val videoUri = TestUtil.bigBuckBunny.toURI

    test("put and get"):
        val cache = JpegCache2(root, 3, .3)
        val path  = root.resolve(s"${getClass.getSimpleName}_put_get.jpg").toAbsolutePath().normalize()
        val jpeg  = Jpeg(videoUri, Duration.ofMillis(250), path, sizeBytes = Some(1000000))

        val jpeg1 = cache.put(jpeg)
        assertEquals(jpeg1, jpeg)

        cache.get(jpeg1) match
            case None        => fail("We did not get any jpeg back. That's unexpected!")
            case Some(jpeg2) =>
                assertEquals(jpeg2.elapsedTime, jpeg1.elapsedTime)
                assertEquals(jpeg2.path, jpeg1.path)
                assertTrue(jpeg2.sizeBytes.isDefined)
                assertTrue(jpeg1.sizeBytes.isDefined)
                assertEquals(jpeg2.sizeBytes.get, jpeg1.sizeBytes.get)
                assertEquals(jpeg2.videoUri, jpeg1.videoUri)
        cache.clearCache()

    test("remove"):
        val cache = JpegCache2(root, 3, .3)
        val path  = root.resolve(s"${getClass.getSimpleName}_remove.jpg").toAbsolutePath().normalize()
        val jpeg  = Jpeg(videoUri, Duration.ofMillis(750), path, sizeBytes = Some(1000000))
        val jpeg1 = Jpeg(videoUri, Duration.ofMillis(250), path, sizeBytes = Some(1000000))
        cache.put(jpeg)
        cache.put(jpeg1)
        assertTrue(cache.get(jpeg).isDefined)
        cache.remove(jpeg)
        assertTrue(cache.get(jpeg).isEmpty)
        assertTrue(cache.get(jpeg1).isDefined)
        cache.clearCache()

    test("freeSpace"):
        val cache = JpegCache2(root, 3, .3)
        val jpegs =
            for i <- 0 until 4
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_freeSpace_$i.jpg").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                Jpeg(videoUri, elapsedTime, path, sizeBytes = Some(1000000))
        jpegs.foreach(cache.put)
        // After 4 × 1 MB puts into a 3 MB cache (clearPct = 0.3), the oldest should be gone
        val head = cache.get(jpegs.head)
        assertTrue(head.isEmpty)
        val last = cache.get(jpegs.last)
        assertTrue(last.isDefined)
        cache.clearCache()

    test("eviction policy"):
        val cache = JpegCache2(root, 2, .3) // maxSize = 2 MB
        val jpegs =
            for i <- 0 until 3
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_eviction_$i.jpg").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                Jpeg(videoUri, elapsedTime, path, sizeBytes = Some(999999))

        jpegs.foreach(cache.put)

        // Oldest (index 0) should be evicted; the newer two should remain
        assertTrue(cache.get(jpegs(0)).isEmpty)
        assertTrue(cache.get(jpegs(1)).isDefined)
        assertTrue(cache.get(jpegs(2)).isDefined)
        cache.clearCache()

    test("totalImages"):
        val cache = JpegCache2(root, 100, .3)
        val paths =
            for i <- 0 until 5
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_total_$i.jpg").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                Jpeg(videoUri, elapsedTime, path, sizeBytes = Some(100))
        paths.foreach(cache.put)
        assertEquals(cache.totalImages, 5)
        cache.clearCache()
        assertEquals(cache.totalImages, 0)
