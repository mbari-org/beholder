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

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.mbari.beholder.AppConfig

import java.net.URI
import java.time.Duration
/**
 * An [[Ffprobe]] that memoizes what the underlying probe returns.
 *
 * Probing a remote video costs an HTTP round trip and the answer never changes for a given URI, so it is worth caching.
 * Beholder is long lived, so the cache is bounded by size rather than allowed to grow forever.
 *
 * @param delegate
 *   Does the actual probing
 * @param maximumSize
 *   How many videos to remember before Caffeine starts evicting
 */
class FfprobeService(
    delegate: Ffprobe = FfprobeUtil,
    maximumSize: Long = AppConfig.Ffprobe.Cache.MaxCount,
    accessTimeout: Duration = AppConfig.Ffprobe.Cache.Expire
) extends Ffprobe:

    private val cache: Cache[URI, VideoSize] =
        Caffeine
            .newBuilder()
            .maximumSize(maximumSize)
            .expireAfterAccess(accessTimeout)
            .build[URI, VideoSize]()

    /**
     * Caffeine runs the loader at most once per key even under concurrent calls, so several simultaneous captures from
     * the same video share a single probe instead of racing.
     *
     * A failed probe returns null from the loader, which Caffeine declines to store. That is deliberate: a video that
     * is briefly unreachable gets probed again next time rather than being remembered as unprobeable.
     */
    override def videoSize(videoUri: URI): Option[VideoSize] =
        Option(cache.get(videoUri, (uri: URI) => delegate.videoSize(uri).orNull))

    /** The number of videos currently remembered. Approximate: Caffeine evicts asynchronously. */
    def estimatedSize: Long = cache.estimatedSize()

    /** Forget everything. Mostly useful in tests. */
    def invalidateAll(): Unit = cache.invalidateAll()

object FfprobeService:

    /** The instance [[FfmpegUtil]] captures with. */
    lazy val default: FfprobeService = FfprobeService()
