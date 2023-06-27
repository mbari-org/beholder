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

import java.nio.file.Paths
import org.junit.Assert.*
import java.time.Duration
import java.nio.file.Files


class JpegCacheSuite extends munit.FunSuite:

  val root = TestUtil.root
  Files.createDirectories(root)
  val videoUrl = TestUtil.bigBuckBunny
  
  /*
    Put an item into a cache and get it back
  */
  test("put and get"):
    val cache = JpegCache(root, 3, .3)
    val path = root.resolve(s"${getClass.getSimpleName}_put_get.jpg").toAbsolutePath().normalize()
    val jpeg = Jpeg(videoUrl, Duration.ofMillis(250), path, sizeBytes = Some(1000000))

    // put
    val jpeg1 = cache.put(jpeg)
    assertEquals(jpeg1, jpeg)

    // get
    cache.get(jpeg1) match 
      case None => fail("We did not get any jpeg back. That's unexpected!")
      case Some(jpeg2) => 
        // Cache changes creationDate. So we can't just compare jpeg1 and jpeg2.
        assertEquals(jpeg2.elapsedTime, jpeg1.elapsedTime)
        assertEquals(jpeg2.path, jpeg1.path)
        assertTrue(jpeg2.sizeBytes.isDefined)
        assertTrue(jpeg1.sizeBytes.isDefined)
        assertEquals(jpeg2.sizeBytes.get, jpeg1.sizeBytes.get)
        assertEquals(jpeg2.videoUrl, jpeg1.videoUrl)
    cache.clearCache()

  /*
    Put two items in a cache. Remove one and confirm it's removed and that
    the second one is still in the cache
  */
  test("remove"):
    val cache = JpegCache(root, 3, .3)
    val path = root.resolve(s"${getClass.getSimpleName}_put_get.jpg").toAbsolutePath().normalize()
    val jpeg = Jpeg(videoUrl, Duration.ofMillis(750), path, sizeBytes = Some(1000000))
    val jpeg1 = Jpeg(videoUrl, Duration.ofMillis(250), path, sizeBytes = Some(1000000))
    cache.put(jpeg)
    cache.put(jpeg1)
    assertTrue(cache.get(jpeg).isDefined)
    cache.remove(jpeg)
    assertTrue(cache.get(jpeg).isEmpty)
    assertTrue(cache.get(jpeg1).isDefined)
    cache.clearCache()

  /*


  */
  test("freeSpace"):
    val cache = JpegCache(root, 3, .3)
    val jpegs = for 
      i   <- 0 until 4
    yield
      val path = root.resolve(s"${getClass.getSimpleName}_freeSpace_$i.jpg").toAbsolutePath().normalize()
      val elapsedTime = Duration.ofMillis(i * 100)
      Jpeg(videoUrl, elapsedTime, path, sizeBytes = Some(1000000))
    jpegs.foreach(cache.put)
    val head = cache.get(jpegs.head)
    assertTrue(head.isEmpty)
    val last = cache.get(jpegs.last)
    assertTrue(last.isDefined)
    cache.clearCache()

  
  
