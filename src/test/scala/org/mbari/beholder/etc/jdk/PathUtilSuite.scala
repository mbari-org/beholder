/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder.etc.jdk

import java.nio.file.Paths
import org.junit.Assert.*
import java.net.URL

class PathUtilSuite extends munit.FunSuite:

  test("isChild") {
    val a = Paths.get("/Users/brian")
    val b = Paths.get("/Users/brian/Documents")
    assertTrue(PathUtil.isChild(a, b))
    assertFalse(PathUtil.isChild(b, a))

    val c = Paths.get("/Users/kevin/Documents/foo")
    assertFalse(PathUtil.isChild(a, c))
    assertFalse(PathUtil.isChild(b, c))
    assertTrue(PathUtil.isChild(c, c))

  }

  test("isJpeg") {
    val a = Paths.get("/Users/brian/Documents/foo.jpg")
    assertTrue(PathUtil.isJpeg(a))
    val b = Paths.get("/Users/brian/Documents/foo.png")
    assertFalse(PathUtil.isJpeg(b))
  }

  test("toPath") {
    val root = Paths.get("/Users/brian")
    val url =  URL("http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    val actual = PathUtil.toPath(root, url)
    val expected = Paths.get("/Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    assertEquals(actual, expected)
  }


  test("fromPath") {
    val root = Paths.get("/Users/brian")
    val path = Paths.get("/Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    val actual = PathUtil.fromPath(root, path)
    assertTrue(actual.isDefined)
    val expected = URL("http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    assertEquals(actual.get, expected)
  }
  
