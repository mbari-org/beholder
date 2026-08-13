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

class AppConfigSuite extends LoggingFunSuite:

    /**
     * The capture pool is sized from the CPU count when it is not configured explicitly. A small container is where
     * that arithmetic goes wrong: one or two processors must still yield a usable pool, because a zero-thread pool is
     * rejected outright and the service would not start at all.
     */
    test("the capture pool is always sized to at least one thread and one queue slot"):
        assert(AppConfig.Capture.Threads >= 1, s"Capture.Threads was ${AppConfig.Capture.Threads}")
        assert(AppConfig.Capture.QueueSize >= 1, s"Capture.QueueSize was ${AppConfig.Capture.QueueSize}")

    test("an ffmpeg capture has a positive timeout, so it can never wait forever"):
        assert(AppConfig.Ffmpeg.Timeout.toMillis > 0, s"Ffmpeg.Timeout was ${AppConfig.Ffmpeg.Timeout}")
        assert(AppConfig.Ffprobe.Timeout.toMillis > 0, s"Ffprobe.Timeout was ${AppConfig.Ffprobe.Timeout}")
