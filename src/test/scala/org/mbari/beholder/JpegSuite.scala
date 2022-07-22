/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
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
  

