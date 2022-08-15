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

import java.nio.file.Files
import java.time.Duration
import org.junit.Assert.*

class JpegCaptureSuite extends munit.FunSuite:

  val root = TestUtil.root
  Files.createDirectories(root)
  val cache = JpegCache(root, 3, .3)
  val capture = JpegCapture(cache)


  test("capture") {
    capture.capture(TestUtil.bigBuckBunny, Duration.ofMillis(1234)) match 
      case Left(_) => fail("Expected an image to be captured and it wasn't")
      case Right(jpeg0) => // Check values
        capture.capture(TestUtil.bigBuckBunny, Duration.ofMillis(1234)) match
          case Left(_) => fail("Expected an image to be in the cache")
          case Right(jpeg1) =>
            // Cache changes creationDate. So we can't just compare jpeg1 and jpeg2.
            assertEquals(jpeg1.elapsedTime, jpeg0.elapsedTime)
            assertEquals(jpeg1.path, jpeg0.path)
            assertTrue(jpeg1.sizeBytes.isDefined)
            assertTrue(jpeg0.sizeBytes.isDefined)
            assertEquals(jpeg1.sizeBytes.get, jpeg0.sizeBytes.get)
            assertEquals(jpeg1.videoUrl, jpeg0.videoUrl)
  }
  
