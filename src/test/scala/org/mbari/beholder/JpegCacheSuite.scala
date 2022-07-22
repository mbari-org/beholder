/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
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
  test("put and get") {
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
        assertEquals(jpeg2, jpeg1)
    cache.clearCache()
  }

  /*
    Put two items in a cache. Remove one and confirm it's removed and that
    the second one is still in the cache
  */
  test("remove") {
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
  }

  /*


  */
  test("freeSpace") {
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
  }

  
  
