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

import org.mbari.beholder.etc.ffmpeg.DurationString.*

import java.net.URI
import java.nio.file.Path
import java.time.Duration

trait ImageCache:

    def root: Path

    /**
     * @param jpeg
     *   The jpeg of interest. This only needs valid url and elapsedTime fields
     * @return
     *   The jpeg in the cache
     */
    def get(jpeg: Jpeg): Option[Jpeg]

    /**
     * Store a jpeg in the cache
     *
     * @param jpeg
     * The jpeg to store
     * @return
     * The stored jpeg
     */
    def put(jpeg: Jpeg): Jpeg

    /**
     * Remove a jpeg from the cache.
     *
     * @param jpeg
     * the jpeg to remove
     */
    def remove(jpeg: Jpeg): Option[Jpeg]

    // -------------------------------

    def get(uri: URI, elapsedTime: DurationString): Option[Jpeg] =
        DurationString.unapply(elapsedTime) match
            case None        => None
            case Some(dur)  => get(uri, dur)

    /**
     * @param uri
     *   The video URL
     * @param elapsedTime
     *   The elapsed time into the video
     */
    def get(uri: URI, elapsedTime: Duration): Option[Jpeg] =
        get(Jpeg.fake(uri, elapsedTime))


    /**
     * Store a jpeg in the cache
     *
     * @param uri
     * The video URL
     * @param elapsedTime
     * The elasped time into the video
     * @param path
     * The on disk location of the jpeg. It must be under the cache's root directory
     */
    def put(uri: URI, elapsedTime: Duration, path: Path): Option[Jpeg] =
        Jpeg.fromPath(root, path).map(put)

    def put(uri: URI, elapsedTime: DurationString, path: Path): Option[Jpeg] =
        DurationString.unapply(elapsedTime) match
            case None => None
            case Some(dur) => put(uri, dur, path)

    def remove(uri: URI, elapsedTime: Duration): Option[Jpeg] =
        remove(Jpeg.fake(uri, elapsedTime))


