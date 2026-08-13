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

import java.time.Duration
import org.junit.Assert.*
import org.mbari.beholder.etc.jdk.PathUtil

class CachedImageSuite extends munit.FunSuite:

    test("toPath_jpg"):
        val root     = TestUtil.root
        val videoUrl = TestUtil.bigBuckBunny
        val duration = Duration.ofMillis(1234)
        val jpeg     = CachedImage.toPath(root, videoUrl.toURI, duration)
        assertTrue(PathUtil.isChild(root, jpeg.path))
        assertEquals(jpeg.elapsedTime, duration)
        assertEquals(jpeg.videoUri, videoUrl.toURI)
        assertEquals(jpeg.path.getFileName().toString(), "00_00_01.234.jpg")

    test("toPath_png"):
        val root     = TestUtil.root
        val videoUrl = TestUtil.bigBuckBunny
        val duration = Duration.ofMillis(1234)
        val png      = CachedImage.toPath(root, videoUrl.toURI, duration, imageType = ImageType.Png)
        assertTrue(PathUtil.isChild(root, png.path))
        assertEquals(png.elapsedTime, duration)
        assertEquals(png.videoUri, videoUrl.toURI)
        assertEquals(png.path.getFileName().toString(), "00_00_01.234.png")

    test("a deinterlaced frame gets its own filename"):
        val root     = TestUtil.root
        val videoUrl = TestUtil.bigBuckBunny
        val duration = Duration.ofMillis(1234)
        val jpeg     = CachedImage.toPath(root, videoUrl.toURI, duration, deinterlace = true)
        val png      = CachedImage.toPath(root, videoUrl.toURI, duration, ImageType.Png, deinterlace = true)
        assertEquals(jpeg.path.getFileName().toString(), "00_00_01.234_deinterlaced.jpg")
        assertEquals(png.path.getFileName().toString(), "00_00_01.234_deinterlaced.png")
        assertTrue(jpeg.deinterlace)
        assertTrue(png.deinterlace)

    test("a deinterlaced frame does not collide with the ordinary capture of the same frame"):
        val root     = TestUtil.root
        val videoUrl = TestUtil.bigBuckBunny
        val duration = Duration.ofMillis(1234)
        val plain    = CachedImage.toPath(root, videoUrl.toURI, duration)
        val deint    = CachedImage.toPath(root, videoUrl.toURI, duration, deinterlace = true)
        assertNotEquals(plain.path, deint.path)
        assertNotEquals(plain.cacheKey, deint.cacheKey)

    /**
     * fromPath has to strip the suffix before it turns underscores into colons. If it does not, the stem parses as an
     * ordinary duration anyway and the frame is silently adopted as a non-deinterlaced entry on the next restart.
     */
    test("fromPath round-trips the deinterlace flag rather than mistaking it for a duration"):
        // Absolute, because toPath always produces absolute paths and fromPath relativizes against the
        // root it is handed — a relative root throws. (See ImageCacheImpl, which uses its root as given.)
        val root     = TestUtil.root.toAbsolutePath.normalize()
        val videoUrl = TestUtil.bigBuckBunny
        val duration = Duration.ofMillis(1234)

        for
            imageType   <- Seq(ImageType.Jpeg, ImageType.Png)
            deinterlace <- Seq(true, false)
        do
            val written  = CachedImage.toPath(root, videoUrl.toURI, duration, imageType, deinterlace)
            val readBack = CachedImage.fromPath(root, written.path)
            assert(readBack.isDefined, s"$written should be readable back off disk")
            assertEquals(readBack.get.elapsedTime, duration, s"elapsed time for $written")
            assertEquals(readBack.get.imageType, imageType, s"image type for $written")
            assertEquals(readBack.get.deinterlace, deinterlace, s"deinterlace flag for $written")
            assertEquals(readBack.get.cacheKey, written.cacheKey, s"cache key for $written")
