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

import com.typesafe.config.ConfigFactory

import java.time.Duration
import scala.util.Try

object AppConfig:

    val Config = ConfigFactory.load()

    val Name: String = "beholder"

    val Description: String = "Framegrab server"

    val Version: String =
        Try(getClass.getPackage.getImplementationVersion).getOrElse("0.0.0-SNAPSHOT")

    object Api:
        val Key: String = Config.getString("beholder.api.key")

    /**
     * Sizing for the pool that runs ffmpeg. Both settings accept 0, meaning "work it out from the machine". Deriving
     * threads from the CPU count has to survive a small container: a quarter of one processor rounds to zero, and a
     * pool of zero threads is refused, so the floor is what keeps the service startable.
     */
    object Capture:
        val Threads: Int =
            Config.getInt("beholder.capture.threads") match
                case configured if configured > 0 => configured
                case _                            => math.max(2, Runtime.getRuntime.availableProcessors / 4)

        val QueueSize: Int =
            Config.getInt("beholder.capture.queuesize") match
                case configured if configured > 0 => configured
                case _                            => Threads * 8

    object Ffmpeg:
        val Path: String      = Config.getString("beholder.ffmpeg.path")
        val Timeout: Duration = Config.getDuration("beholder.ffmpeg.timeout")

    object Ffprobe:
        object Cache:
            val MaxCount: Int    = Config.getInt("beholder.ffprobe.cache.maxcount")
            val Expire: Duration = Config.getDuration("beholder.ffprobe.cache.expire")
        val Path: String      = Config.getString("beholder.ffprobe.path")
        val Timeout: Duration = Config.getDuration("beholder.ffprobe.timeout")

    object Http:
        val Port: Int = Config.getInt("beholder.http.port")

    object Cache:
        val sizeMb: Int     = Config.getInt("beholder.cache.size")
        val freePct: Double = Config.getDouble("beholder.cache.freepct")
