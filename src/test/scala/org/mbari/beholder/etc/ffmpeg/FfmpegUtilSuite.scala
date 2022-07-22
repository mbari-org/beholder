/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
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
