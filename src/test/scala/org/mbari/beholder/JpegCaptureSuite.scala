/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder

import java.nio.file.Files
import java.time.Duration

class JpegCaptureSuite extends munit.FunSuite:

  val root = TestUtil.root
  Files.createDirectories(root)
  val cache = JpegCache(root, 3, .3)
  val capture = JpegCapture(cache)


  test("capture") {
    capture.capture(TestUtil.bigBuckBunny, Duration.ofMillis(1234)) match 
      case None => fail("Expected an image to be captured and it wasn't")
      case Some(jpeg0) => // Check values
        capture.capture(TestUtil.bigBuckBunny, Duration.ofMillis(1234)) match
          case None => fail("Expected an image to be in the cache")
          case Some(jpeg1) =>
            assertEquals(jpeg1, jpeg0)
  }
  
