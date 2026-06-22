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

class ImageCacheImplSuite extends munit.FunSuite:

    val root     = TestUtil.root
    Files.createDirectories(root)
    val videoUri = TestUtil.bigBuckBunny.toURI

    /**
     * Don't compare the creation date. It's not guaranteed to be the same.
     * @param a
     * @param b
     * @return
     */
    def isEqual(a: CachedImage, b: CachedImage): Boolean =
        a.elapsedTime == b.elapsedTime &&
        a.path == b.path &&
        a.sizeBytes == b.sizeBytes &&
        a.videoUri == b.videoUri &&
        a.imageType == b.imageType

    // ---- JPEG tests ----

    test("put and get"):
        val cache = ImageCacheImpl(root, 3, .3)
        val path  = root.resolve(s"${getClass.getSimpleName}_put_get.jpg").toAbsolutePath().normalize()
        val jpeg  = CachedImage(videoUri, Duration.ofMillis(250), path, sizeBytes = Some(1000000))

        val jpeg1 = cache.put(jpeg)
        assertTrue(isEqual(jpeg1, jpeg))
        assertTrue(jpeg.created.isBefore(jpeg1.created))

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
        val cache = ImageCacheImpl(root, 3, .3)
        val path  = root.resolve(s"${getClass.getSimpleName}_remove.jpg").toAbsolutePath().normalize()
        val jpeg  = CachedImage(videoUri, Duration.ofMillis(750), path, sizeBytes = Some(1000000))
        val jpeg1 = CachedImage(videoUri, Duration.ofMillis(250), path, sizeBytes = Some(1000000))
        cache.put(jpeg)
        cache.put(jpeg1)
        assertTrue(cache.get(jpeg).isDefined)
        cache.remove(jpeg)
        assertTrue(cache.get(jpeg).isEmpty)
        assertTrue(cache.get(jpeg1).isDefined)
        cache.clearCache()

    test("freeSpace"):
        val cache = ImageCacheImpl(root, 3, .3)
        val jpegs =
            for i <- 0 until 4
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_freeSpace_$i.jpg").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                CachedImage(videoUri, elapsedTime, path, sizeBytes = Some(1000000))
        jpegs.foreach(cache.put)
        // After 4 × 1 MB puts into a 3 MB cache (clearPct = 0.3), the oldest should be gone
        val head = cache.get(jpegs.head)
        assertTrue(head.isEmpty)
        val last = cache.get(jpegs.last)
        assertTrue(last.isDefined)
        cache.clearCache()

    test("eviction policy"):
        val cache = ImageCacheImpl(root, 2, .3) // maxSize = 2 MB
        val jpegs =
            for i <- 0 until 3
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_eviction_$i.jpg").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                CachedImage(videoUri, elapsedTime, path, sizeBytes = Some(999999))

        jpegs.foreach(cache.put)

        // Oldest (index 0) should be evicted; the newer two should remain
        assertTrue(cache.get(jpegs(0)).isEmpty)
        assertTrue(cache.get(jpegs(1)).isDefined)
        assertTrue(cache.get(jpegs(2)).isDefined)
        cache.clearCache()

    test("totalImages"):
        val cache = ImageCacheImpl(root, 100, .3)
        val paths =
            for i <- 0 until 5
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_total_$i.jpg").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                CachedImage(videoUri, elapsedTime, path, sizeBytes = Some(100))
        paths.foreach(cache.put)
        assertEquals(cache.totalImages, 5)
        cache.clearCache()
        assertEquals(cache.totalImages, 0)

    // ---- PNG tests ----

    test("put and get (png)"):
        val cache = ImageCacheImpl(root, 3, .3)
        val path  = root.resolve(s"${getClass.getSimpleName}_put_get.png").toAbsolutePath().normalize()
        val png   = CachedImage(videoUri, Duration.ofMillis(250), path, sizeBytes = Some(1000000), imageType = ImageType.Png)

        val png1 = cache.put(png)
        assertTrue(isEqual(png1, png))
        assertTrue(png.created.isBefore(png1.created))

        cache.get(png1) match
            case None       => fail("We did not get any png back. That's unexpected!")
            case Some(png2) =>
                assertEquals(png2.elapsedTime, png1.elapsedTime)
                assertEquals(png2.path, png1.path)
                assertTrue(png2.sizeBytes.isDefined)
                assertTrue(png1.sizeBytes.isDefined)
                assertEquals(png2.sizeBytes.get, png1.sizeBytes.get)
                assertEquals(png2.videoUri, png1.videoUri)
                assertEquals(png2.imageType, ImageType.Png)
        cache.clearCache()

    test("remove (png)"):
        val cache = ImageCacheImpl(root, 3, .3)
        val path  = root.resolve(s"${getClass.getSimpleName}_remove.png").toAbsolutePath().normalize()
        val png   = CachedImage(videoUri, Duration.ofMillis(750), path, sizeBytes = Some(1000000), imageType = ImageType.Png)
        val png1  = CachedImage(videoUri, Duration.ofMillis(250), path, sizeBytes = Some(1000000), imageType = ImageType.Png)
        cache.put(png)
        cache.put(png1)
        assertTrue(cache.get(png).isDefined)
        cache.remove(png)
        assertTrue(cache.get(png).isEmpty)
        assertTrue(cache.get(png1).isDefined)
        cache.clearCache()

    test("freeSpace (png)"):
        val cache = ImageCacheImpl(root, 3, .3)
        val pngs  =
            for i <- 0 until 4
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_freeSpace_$i.png").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                CachedImage(videoUri, elapsedTime, path, sizeBytes = Some(1000000), imageType = ImageType.Png)
        pngs.foreach(cache.put)
        // After 4 × 1 MB puts into a 3 MB cache (clearPct = 0.3), the oldest should be gone
        val head = cache.get(pngs.head)
        assertTrue(head.isEmpty)
        val last = cache.get(pngs.last)
        assertTrue(last.isDefined)
        cache.clearCache()

    test("eviction policy (png)"):
        val cache = ImageCacheImpl(root, 2, .3)
        val pngs  =
            for i <- 0 until 3
            yield
                val path        = root.resolve(s"${getClass.getSimpleName}_eviction_$i.png").toAbsolutePath().normalize()
                val elapsedTime = Duration.ofMillis(i * 100)
                CachedImage(videoUri, elapsedTime, path, sizeBytes = Some(999999), imageType = ImageType.Png)
        pngs.foreach(cache.put)
        assertTrue(cache.get(pngs(0)).isEmpty)
        assertTrue(cache.get(pngs(1)).isDefined)
        assertTrue(cache.get(pngs(2)).isDefined)
        cache.clearCache()

    // ---- Mixed-type tests ----

    test("same key different type"):
        // JPEG and PNG at identical (videoUri, elapsedTime) must be independent cache entries.
        val cache    = ImageCacheImpl(root, 100, .3)
        val elapsed  = Duration.ofMillis(500)
        val jpegPath = root.resolve(s"${getClass.getSimpleName}_mixed.jpg").toAbsolutePath().normalize()
        val pngPath  = root.resolve(s"${getClass.getSimpleName}_mixed.png").toAbsolutePath().normalize()
        val jpeg     = CachedImage(videoUri, elapsed, jpegPath, sizeBytes = Some(100))
        val png      = CachedImage(videoUri, elapsed, pngPath, sizeBytes = Some(200), imageType = ImageType.Png)

        cache.put(jpeg)
        cache.put(png)
        assertEquals(cache.totalImages, 2)

        val gotJpeg = cache.get(jpeg)
        val gotPng  = cache.get(png)
        assertTrue(gotJpeg.isDefined)
        assertTrue(gotPng.isDefined)
        assertEquals(gotJpeg.get.imageType, ImageType.Jpeg)
        assertEquals(gotPng.get.imageType, ImageType.Png)

        // Removing JPEG must not disturb the PNG entry
        cache.remove(jpeg)
        assertTrue(cache.get(jpeg).isEmpty)
        assertTrue(cache.get(png).isDefined)
        assertEquals(cache.totalImages, 1)
        cache.clearCache()

    test("totalImages (mixed types)"):
        val cache   = ImageCacheImpl(root, 100, .3)
        val elapsed = Duration.ofMillis(1000)
        val jpegPath = root.resolve(s"${getClass.getSimpleName}_total_mixed.jpg").toAbsolutePath().normalize()
        val pngPath  = root.resolve(s"${getClass.getSimpleName}_total_mixed.png").toAbsolutePath().normalize()
        val jpeg     = CachedImage(videoUri, elapsed, jpegPath, sizeBytes = Some(100))
        val png      = CachedImage(videoUri, elapsed, pngPath, sizeBytes = Some(100), imageType = ImageType.Png)
        cache.put(jpeg)
        cache.put(png)
        assertEquals(cache.totalImages, 2)
        cache.clearCache()
        assertEquals(cache.totalImages, 0)
