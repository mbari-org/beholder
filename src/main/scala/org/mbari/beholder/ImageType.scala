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

import org.mbari.beholder.etc.jdk.PathUtil

import java.nio.file.Path

/**
 * The image type. Used to determine the image type of a cached image.
 * @param extension The extension for the image type (e.g. ".jpg", ".png")
 * @param mediaType The media type (e.g. "image/jpeg", "image/png")
 * @param evictionOrder The order in which the image type is evicted from the cache. Lower values are evicted first.
 */
enum ImageType(val extension: String, val mediaType: String, val evictionOrder: Int):
    case Jpeg extends ImageType(".jpg", "image/jpeg", 2)
    case Png  extends ImageType(".png", "image/png", 1)

object ImageType:
    def fromExtension(ext: String): Option[ImageType] =
        ImageType.values.find(_.extension == ext)

    def fromPath(path: Path): Option[ImageType] =
        if PathUtil.isJpeg(path) then Some(Jpeg)
        else if PathUtil.isPng(path) then Some(Png)
        else None
