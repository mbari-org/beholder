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

import java.time.Duration
import org.junit.Assert.*
import org.mbari.beholder.etc.jdk.PathUtil

class JpegSuite extends munit.FunSuite:

  test("toPath") {
    val root = TestUtil.root
    val videoUrl = TestUtil.bigBuckBunny
    val duration = Duration.ofMillis(1234)
    val jpeg = Jpeg.toPath(root, videoUrl, duration)
    assertTrue(PathUtil.isChild(root, jpeg.path))
    assertEquals(jpeg.elapsedTime, duration)
    assertEquals(jpeg.videoUrl, videoUrl)
    assertEquals(jpeg.path.getFileName().toString(), "00_00_01.234.jpg")
  }
  

