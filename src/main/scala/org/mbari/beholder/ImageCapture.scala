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

import java.net.URI
import java.time.Duration
import org.mbari.beholder.etc.ffmpeg.{FfmpegUtil, Ffprobe, FfprobeService}
import org.mbari.beholder.etc.jdk.Logging.given
import org.mbari.beholder.etc.jdk.DurationUtil

import java.nio.file.{Files, Path}
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap}
import org.mbari.beholder.etc.jdk.PathUtil
import org.mbari.beholder.api.ErrorMsg
import org.mbari.beholder.api.StatusMsg
import org.mbari.beholder.api.ServerError

import scala.util.Try
import scala.util.Failure
import scala.util.Success

/**
 * How a single frame is pulled out of a video: `(videoUri, elapsedTime, target, accurate, skipNonKeyFrames,
 * deinterlace)`.
 *
 * Injectable so that [[ImageCapture]] can be exercised without paying for a real ffmpeg run, in the same spirit as
 * `FfprobeService` taking its delegate.
 */
type FrameGrabber = (URI, Duration, Path, Boolean, Boolean, Boolean) => Either[Throwable, Path]

/**
 * @param cache
 *   Where captured frames are stored and looked up
 * @param grabFrameFrom
 *   Does the actual capture
 * @param ffprobe
 *   Used to decide whether a video is worth deinterlacing. Defaults to the same cached instance `FfmpegUtil` probes
 *   with, so asking here costs nothing that the capture was not about to pay anyway.
 */
class ImageCapture(
    cache: ImageCache,
    grabFrameFrom: FrameGrabber = ImageCapture.ffmpegFrameGrabber,
    ffprobe: Ffprobe = FfprobeService.default
):

    private val log = System.getLogger(getClass.getName)

    /**
     * Captures currently running requests, keyed by the file each one is producing.
     *
     * Without this, a burst of requests for the same frame all miss the cache and all shell out, so the same frame is
     * decoded N times — and, because the target path is derived from the request rather than made unique, N ffmpeg
     * processes write the same file at once and a caller can be handed a half-written frame.
     */
    private val inFlight = ConcurrentHashMap[Path, CompletableFuture[Either[ErrorMsg, CachedImage]]]()

    /**
     * Capture a frame from the video at the specified elapsed time. If the frame is not already in the cache, it will
     * be captured using ffmpeg and stored in the cache.
     *
     * @param videoUri
     *   The URL of the video to capture from
     * @param elapsedTime
     *   The elapsed time into the video to capture the frame
     * @param accurate
     *   If true, the frame will be captured at the exact elapsed time. If false, the frame will be captured at the
     *   nearest keyframe.
     * @param skipNonKeyFrames
     *   If true, the capture will skip non-key frames. This is useful for videos that do not have keyframes at regular
     *   intervals.
     * @param imageType
     *   The type of image to capture.
     * @param deinterlace
     *   Request that an interlaced video be deinterlaced. Only a request: see [[resolveDeinterlace]] for what actually
     *   decides it.
     * @return
     *   On success, a Right containing the information and location on disk of the captured image. On failure, a Left
     *   containing an ErrorMsg.
     */
    def capture(
        videoUri: URI,
        elapsedTime: Duration,
        accurate: Boolean = true,
        skipNonKeyFrames: Boolean = false,
        imageType: ImageType = ImageType.Jpeg,
        deinterlace: Boolean = false
    ): Either[ErrorMsg, CachedImage] =
        val deinterlaced = resolveDeinterlace(videoUri, deinterlace, skipNonKeyFrames)
        cache.get(videoUri, elapsedTime, imageType, deinterlaced) match
            case Some(cachedImage) => Right(cachedImage)
            case None              =>
                val cachedImage =
                    CachedImage.toPath(cache.root, videoUri, elapsedTime, imageType, deinterlaced)
                if !PathUtil.isChild(cache.root, cachedImage.path) then
                    Left(ServerError("An invalid cache path was calculated"))
                else
                    coalesced(cachedImage):
                        grabFrame(videoUri, elapsedTime, accurate, skipNonKeyFrames, imageType, cachedImage)

    /**
     * Turn a requested `deinterlace` into what we are actually going to do about it.
     *
     * This is the single place that decision gets made. It has to be, because it names the cache file as well as
     * building the ffmpeg command — if [[FfmpegUtil]] second-guessed the flag, a frame could be stored under a name
     * claiming a deinterlace that never ran, and every later request would be served that mislabelled frame.
     *
     * Three things make it false:
     *   - nobody asked
     *   - `skipNonKeyFrames` is set, in which case the frames the deinterlacer would see are whole keyframes apart, so
     *     its motion detection has nothing coherent to compare and the result is not worth having
     *   - the video is not interlaced, or could not be probed at all
     *
     * The first two are checked before the probe, so a request that cannot be deinterlaced never pays for one.
     *
     * Resolving to the *effective* answer rather than the requested one is also what keeps the cache honest: a
     * progressive video asked for with `deinterlace=true` yields the same pixels as an ordinary capture, so it shares
     * the ordinary capture's entry instead of duplicating it under another name.
     */
    private def resolveDeinterlace(videoUri: URI, requested: Boolean, skipNonKeyFrames: Boolean): Boolean =
        if !requested then false
        else if skipNonKeyFrames then
            log.atDebug.log(() => s"Ignoring deinterlace for $videoUri: it cannot be combined with skipNonKeyFrames")
            false
        else ffprobe.isInterlaced(videoUri)

    /**
     * Runs `work` for `target`, or — if a capture of the same frame is already running — waits for that one and returns
     * its result.
     *
     * The key is the target path, which is to say the cache key. `accurate` and `skipNonKeyFrames` are deliberately not
     * part of it: the cache is not keyed on them either, so a request that differs only in those flags already gets
     * whichever frame was cached first, and coalescing on the same identity as the cache keeps the two consistent.
     *
     * The effective `deinterlace` *is* part of it, for the same reason — it is part of the path, so it is part of the
     * cache key, so two requests that will produce different pixels are never merged into one capture.
     *
     * Note that `work` runs outside the map, not inside `computeIfAbsent`: that method holds a bin lock for the
     * duration of its mapping function, which would stall unrelated captures that happen to hash to the same bin for as
     * long as an ffmpeg run takes.
     */
    private def coalesced(
        cachedImage: CachedImage
    )(work: => Either[ErrorMsg, CachedImage]): Either[ErrorMsg, CachedImage] =
        val target   = cachedImage.path
        val mine     = CompletableFuture[Either[ErrorMsg, CachedImage]]()
        val existing = inFlight.putIfAbsent(target, mine)

        if existing != null then
            log.atDebug.log(() => s"Joining an in-flight capture of $target")
            existing.join()
        else
            try
                // The previous leader for this frame may have finished and cached it between our
                // cache lookup and our claim here, which would make this a needless second run.
                val result = cache.get(cachedImage) match
                    case Some(alreadyCached) => Right(alreadyCached)
                    case None                => work
                mine.complete(result)
                result
            finally
                // A no-op if the try block already completed it. It matters when `work` throws:
                // without it, everyone waiting on this frame would wait forever.
                mine.complete(Left(ServerError("The capture failed unexpectedly")))
                inFlight.remove(target, mine)

    private def grabFrame(
        videoUri: URI,
        elapsedTime: Duration,
        accurate: Boolean,
        skipNonKeyFrames: Boolean,
        imageType: ImageType,
        cachedImage: CachedImage
    ): Either[ErrorMsg, CachedImage] =
        val parent = cachedImage.path.getParent()
        if !Files.exists(parent) then Files.createDirectories(parent)
        // Take the flag off the CachedImage, not off a separate argument: it is the same value that
        // named the file, so the frame and its name cannot disagree about what was done to it.
        grabFrameFrom(
            videoUri,
            elapsedTime,
            cachedImage.path,
            accurate,
            skipNonKeyFrames,
            cachedImage.deinterlace
        ) match
            case Left(e)     =>
                log
                    .withCause(e)
                    .atDebug
                    .log(() => s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUri")
                Left(StatusMsg(s"Failed to capture frame from $videoUri at $elapsedTime", 500))
            case Right(path) =>
                Try:
                    val sizeBytes = Files.size(path)
                    val theImage  = cachedImage.copy(path = path, sizeBytes = Some(sizeBytes))
                    cache.put(theImage)
                    log.atDebug
                        .log(() =>
                            s"Captured image (${imageType.mediaType}) at ${DurationUtil.toHMS(elapsedTime)} from $videoUri"
                        )
                    theImage
                match
                    case Failure(exception) =>
                        log
                            .withCause(exception)
                            .atError
                            .log(() => s"Failed to capture image at ${DurationUtil.toHMS(elapsedTime)} from $videoUri")
                        Left(StatusMsg(s"Failed to capture frame from $videoUri at $elapsedTime", 500))
                    case Success(value)     => Right(value)

    def findInCache(
        videoUri: URI,
        elapsedTime: Duration,
        imageType: ImageType,
        deinterlace: Boolean
    ): Option[CachedImage] =
        cache.get(videoUri, elapsedTime, imageType, deinterlace)

object ImageCapture:

    /**
     * The real capture, adapted to [[FrameGrabber]].
     *
     * Spelled out rather than written with placeholders because `frameCapture` takes `timeout` between the flags we
     * pass positionally and the one we care about here, so `_`s would quietly line `deinterlace` up with the timeout.
     */
    val ffmpegFrameGrabber: FrameGrabber =
        (videoUri, elapsedTime, target, accurate, skipNonKeyFrames, deinterlace) =>
            FfmpegUtil.frameCapture(
                videoUri,
                elapsedTime,
                target,
                accurate,
                skipNonKeyFrames,
                deinterlace = deinterlace
            )
