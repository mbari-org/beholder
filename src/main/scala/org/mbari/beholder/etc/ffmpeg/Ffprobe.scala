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
 * How a video stream's lines are ordered, as reported by ffprobe's `field_order`.
 *
 * The names are ffprobe's own tokens rather than something more readable, so that what you see here matches what you
 * see when you run ffprobe by hand.
 */
enum FieldOrder(val isInterlaced: Boolean):

    /** Not interlaced. */
    case Progressive extends FieldOrder(false)

    /** Top field coded first, top field displayed first. */
    case Tt extends FieldOrder(true)

    /** Bottom field coded first, bottom field displayed first. */
    case Bb extends FieldOrder(true)

    /** Top field coded first, bottom field displayed first. */
    case Tb extends FieldOrder(true)

    /** Bottom field coded first, top field displayed first. */
    case Bt extends FieldOrder(true)

    /**
     * ffprobe could not tell, or reported something we do not recognize.
     *
     * Treated as *not* interlaced on purpose: deinterlacing a progressive frame visibly softens it, so when we are
     * guessing, the cheaper mistake is to leave the frame alone.
     */
    case Unknown extends FieldOrder(false)

object FieldOrder:

    /** Parse ffprobe's `field_order` value. Anything unrecognized, missing, or empty becomes [[Unknown]]. */
    def parse(value: String): FieldOrder =
        value.trim.toLowerCase match
            case "progressive" => Progressive
            case "tt"          => Tt
            case "bb"          => Bb
            case "tb"          => Tb
            case "bt"          => Bt
            case _             => Unknown

/**
 * What a probe tells us about a video's first video stream.
 *
 * @param width
 *   The declared width
 * @param height
 *   The declared height
 * @param fieldOrder
 *   Whether the stream is interlaced, and how
 */
case class VideoInfo(width: Int, height: Int, fieldOrder: FieldOrder = FieldOrder.Unknown):
    def size: VideoSize       = VideoSize(width, height)
    def isInterlaced: Boolean = fieldOrder.isInterlaced

/**
 * Inspects a video before we capture a frame from it.
 */
trait Ffprobe:

    /**
     * Everything we ask ffprobe about a video, in one call.
     *
     * It is deliberately one call rather than one per property: a probe of a remote video costs an HTTP round trip, so
     * the size and the field order are worth fetching together even when a given capture only needs one of them.
     *
     * @param videoUri
     *   The video to inspect
     * @return
     *   What the probe found, or None if the video could not be probed
     */
    def probe(videoUri: URI): Option[VideoInfo]

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
    def videoSize(videoUri: URI): Option[VideoSize] = probe(videoUri).map(_.size)

    /**
     * Whether a video is interlaced, and so worth running a deinterlacer over.
     *
     * False when the video cannot be probed at all — see [[FieldOrder.Unknown]] for why an unprobeable video is left
     * alone rather than deinterlaced on spec.
     *
     * @param videoUri
     *   The video to inspect
     * @return
     *   true if the first video stream declares an interlaced field order
     */
    def isInterlaced(videoUri: URI): Boolean = probe(videoUri).exists(_.isInterlaced)
