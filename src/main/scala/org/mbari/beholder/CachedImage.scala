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

import java.net.URL
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
 */
case class CachedImage(
                   videoUri: URI,
                   elapsedTime: Duration,
                   path: Path,
                   created: Instant = Instant.now(),
                   sizeBytes: Option[Long] = None,
                   imageType: ImageType = ImageType.Jpeg
):
    val sizeMB: Option[Double] = sizeBytes.map(NumberUtil.byteToMB)

object CachedImage:

    /**
     * Generates cachedImage info
     * @param root
     *   The root directory of the cache
     * @param uri
     *   The video url
     * @param elapsedTime
     *   The elapsed tie into the video
     * @return
     *   The cachedImage info
     */
    def toPath(root: Path, uri: URI, elapsedTime: Duration, imageType: ImageType = Jpeg): CachedImage =
        val filename = DurationUtil.toHMS(elapsedTime).replace(":", "_") + imageType.extension
        val parent   = PathUtil.toPath(root, uri.toURL)
        val path     = parent.resolve(filename)
        CachedImage(uri, elapsedTime, path, imageType = imageType)

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
        if !Files.isDirectory(file) && PathUtil.isChild(root, file) && (PathUtil.isJpeg(file) || PathUtil.isPng(file)) then
            val parent = file.getParent
            val opt = ImageType.fromPath(file)

            opt.flatMap(imageType => {
                PathUtil
                    .fromPath(root, parent)
                    .map(videoUrl =>
                        val filename = PathUtil.dropExtension(file).replace("_", ":")
                        val elapsedTime = DurationUtil.fromHMS(filename)
                        val size = if Files.isRegularFile(file) then Some(Files.size(file)) else None
                        CachedImage(videoUrl.toURI, elapsedTime, file, sizeBytes = size, imageType = imageType)
                    )
            })


        else None

    private val fakeUrl  = URI.create("http://www.mbari.org")
    private val fakePath = Paths.get("/foo/bar")

    /**
     * Constructs a fake/mock jpeg that is useful for searchies
     * @param elapsedTime
     */
    def fake(elapsedTime: Duration, imageType: ImageType): CachedImage =
        val path = PathUtil.useExtension(fakePath, imageType.extension)
        CachedImage(fakeUrl, elapsedTime, path, imageType = imageType )

    def fake(uri: URI, elapsedTime: Duration, imageType: ImageType): CachedImage =
        val path = PathUtil.useExtension(fakePath, imageType.extension)
        CachedImage(uri, elapsedTime, path, imageType = imageType)
