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

import java.time.Duration
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.mbari.beholder.TestUtil

class FfmpegUtilSuite extends munit.FunSuite:

  private val videoUrl = TestUtil.bigBuckBunny
  
  test("frameCapture") {
    val path = Paths.get("target", "trashme.jpg")
    FfmpegUtil.frameCapture(videoUrl, Duration.ofMillis(250), path) match
      case Left(e) =>
        fail(s"File was not created at $path")
      case Right(v) =>
        val exists = Files.exists(path)
        assertTrue(s"File was not created at $path", exists)
    if (Files.exists(path))
          Files.delete(path)
  }
