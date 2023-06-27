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

package org.mbari.beholder.etc.jdk

import java.nio.file.Paths
import org.junit.Assert.*
import java.net.URL

class PathUtilSuite extends munit.FunSuite:

  test("isChild"):
    val a = Paths.get("/Users/brian")
    val b = Paths.get("/Users/brian/Documents")
    assertTrue(PathUtil.isChild(a, b))
    assertFalse(PathUtil.isChild(b, a))

    val c = Paths.get("/Users/kevin/Documents/foo")
    assertFalse(PathUtil.isChild(a, c))
    assertFalse(PathUtil.isChild(b, c))
    assertTrue(PathUtil.isChild(c, c))


  test("isJpeg"):
    val a = Paths.get("/Users/brian/Documents/foo.jpg")
    assertTrue(PathUtil.isJpeg(a))
    val b = Paths.get("/Users/brian/Documents/foo.png")
    assertFalse(PathUtil.isJpeg(b))

  test("toPath"):
    val root = Paths.get("/Users/brian")
    val url =  URL("http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    val actual = PathUtil.toPath(root, url)
    val expected = Paths.get("/Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    assertEquals(actual, expected)


  test("fromPath"):
    val root = Paths.get("/Users/brian")
    val path = Paths.get("/Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    val actual = PathUtil.fromPath(root, path)
    assertTrue(actual.isDefined)
    val expected = URL("http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
    assertEquals(actual.get, expected)
  
