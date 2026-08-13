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

import org.mbari.beholder.ImageType.Jpeg

import java.nio.file.{Files, Path, Paths}
import java.time.{Duration, Instant}
import org.mbari.beholder.etc.jdk.{DurationUtil, PathUtil}
import org.mbari.beholder.util.NumberUtil

import java.net.URI

/**
 * Information about the source of a JPEG
 * @param videoUri
 *   The URL to the source video
 * @param elapsedTime
 *   The elapsed time into the video that the jpeg was taken
 * @param path
 *   The local path to the jpeg file
 * @param created
 *   When the jpeg was created. Used by the cache to determine which items to drop.
 * @param sizeBytes
 *   The size of the jpeg file in bytes
 * @param deinterlace
 *   Whether a deinterlacer was actually run over this frame. This is the *effective* flag, not what the request asked
 *   for: a progressive video captured with `deinterlace=true` is stored here as false, because the pixels are identical
 *   to an ordinary capture and there is nothing to be gained by holding two copies of them.
 */
case class CachedImage(
    videoUri: URI,
    elapsedTime: Duration,
    path: Path,
    created: Instant = Instant.now(),
    sizeBytes: Option[Long] = None,
    imageType: ImageType = ImageType.Jpeg,
    deinterlace: Boolean = false
):
    val sizeMB: Option[Double] = sizeBytes.map(NumberUtil.byteToMB)

    /**
     * What the cache indexes this image by, within its video.
     */
    def cacheKey: (Long, ImageType, Boolean) = (elapsedTime.toMillis, imageType, deinterlace)

object CachedImage:

    /**
     * Marks a cached frame that a deinterlacer was run over, so it cannot be confused with the interlaced capture of
     * the same frame.
     */
    val DeinterlacedSuffix = "_deinterlaced"

    /**
     * Generates cachedImage info
     * @param root
     *   The root directory of the cache
     * @param uri
     *   The video url
     * @param elapsedTime
     *   The elapsed tie into the video
     * @param deinterlace
     *   Whether a deinterlacer was run over the frame. Deinterlaced frames live beside their interlaced counterparts
     *   under a [[DeinterlacedSuffix]]-marked name, so that asking for one never returns the other.
     * @return
     *   The cachedImage info
     */
    def toPath(
        root: Path,
        uri: URI,
        elapsedTime: Duration,
        imageType: ImageType = Jpeg,
        deinterlace: Boolean = false
    ): CachedImage =
        val suffix   = if deinterlace then DeinterlacedSuffix else ""
        val filename = DurationUtil.toHMS(elapsedTime).replace(":", "_") + suffix + imageType.extension
        val parent   = PathUtil.toPath(root, uri.toURL)
        val path     = parent.resolve(filename)
        CachedImage(uri, elapsedTime, path, imageType = imageType, deinterlace = deinterlace)

    /**
     * Generates cachedImage info
     * @param root
     *   The root directory of the cache
     * @param file
     *   the jpeg file (must be under the root fo the cache directory)
     * @return
     *   A cachedImage info. None the file is not a cachedImage or if it's not under the cache directory
     */
    def fromPath(root: Path, file: Path): Option[CachedImage] =
        if !Files.isDirectory(file) && PathUtil.isChild(root, file) && (PathUtil.isJpeg(file) || PathUtil.isPng(file))
        then
            val parent = file.getParent
            val opt    = ImageType.fromPath(file)

            opt.flatMap(imageType =>
                PathUtil
                    .fromPath(root, parent)
                    .map(videoUrl =>
                        val stem = PathUtil.dropExtension(file)

                        // The suffix has to come off *before* the underscores become colons. Leave it on and
                        // "00_00_01.234_deinterlaced" turns into "00:00:01.234:deinterlaced"
                        val deinterlaced = stem.endsWith(DeinterlacedSuffix)
                        val hms          = (if deinterlaced then stem.dropRight(DeinterlacedSuffix.length) else stem)
                            .replace("_", ":")

                        val elapsedTime = DurationUtil.fromHMS(hms)
                        val size        = if Files.isRegularFile(file) then Some(Files.size(file)) else None
                        CachedImage(
                            videoUrl.toURI,
                            elapsedTime,
                            file,
                            sizeBytes = size,
                            imageType = imageType,
                            deinterlace = deinterlaced
                        )
                    )
            )
        else None

    private val fakeUrl  = URI.create("http://www.mbari.org")
    private val fakePath = Paths.get("/foo/bar")

    /**
     * Constructs a fake/mock jpeg that is useful for searchies
     * @param elapsedTime
     */
    def fake(elapsedTime: Duration, imageType: ImageType, deinterlace: Boolean): CachedImage =
        val path = PathUtil.useExtension(fakePath, imageType.extension)
        CachedImage(fakeUrl, elapsedTime, path, imageType = imageType, deinterlace = deinterlace)

    // Only one alternative of an overloaded method may carry defaults
    def fake(uri: URI, elapsedTime: Duration, imageType: ImageType, deinterlace: Boolean = false): CachedImage =
        val path = PathUtil.useExtension(fakePath, imageType.extension)
        CachedImage(uri, elapsedTime, path, imageType = imageType, deinterlace = deinterlace)
