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

/**
 * The size of a video's first video stream, as declared by the codec.
 */
case class VideoSize(width: Int, height: Int)

/**
 * Inspects a video before we capture a frame from it.
 */
trait Ffprobe:

    /**
     * The display size of a video's first video stream.
     *
     * This is the size the codec declares, and it is deliberately NOT the clean aperture (clap) size: ffprobe reports
     * `width`/`height` before any container level cropping is applied, and exposes the clap separately as "Frame
     * Cropping" side data. That makes it exactly the size we want to crop to when the decoder has been told to skip
     * cropping entirely.
     *
     * @param videoUri
     *   The video to inspect
     * @return
     *   The size, or None if the video could not be probed
     */
    def videoSize(videoUri: URI): Option[VideoSize]
