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
import java.net.URI

class PathUtilSuite extends munit.FunSuite:

    test("useExtension"):
        val path    = Paths.get("/Users/brian/foo.jpg")
        val newPath = PathUtil.useExtension(path, ".png")
        assertEquals(newPath.toString, "/Users/brian/foo.png")

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
        val root     = Paths.get("/Users/brian")
        val url      = URI
            .create(
                "http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4"
            )
            .toURL()
        val actual   = PathUtil.toPath(root, url)
        val expected = Paths.get(
            "/Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4"
        )
        assertEquals(actual, expected)

    test("fromPath"):
        val root     = Paths.get("/Users/brian")
        val path     = Paths.get(
            "/Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4"
        )
        val actual   = PathUtil.fromPath(root, path)
        assertTrue(actual.isDefined)
        val expected = URI
            .create(
                "http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4"
            )
            .toURL()
        assertEquals(actual.get, expected)

    /**
     * `toPath` always returns an absolute path, and nothing normalizes the cache root on the way in — beholder takes
     * `<cacheRoot>` straight from the command line. So `fromPath` can be handed a relative root and an absolute path,
     * and `Path.relativize` refuses to mix the two. `isChild` already normalizes both sides; this must agree with it,
     * or it says "yes that is under the root" and then throws trying to say where.
     */
    test("fromPath round-trips a relative cache root"):
        val root = Paths.get("target", "relative_cache_root")
        val url  = URI
            .create(
                "http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4"
            )
            .toURL()
        val path = PathUtil.toPath(root, url)
        assertTrue("isChild should accept a relative root", PathUtil.isChild(root, path))
        assertEquals(PathUtil.fromPath(root, path), Some(url))

    test("fromPath round-trips a root with . and .. in it"):
        val root = Paths.get("target", "..", "target", "unnormalized_root")
        val url  = URI.create("http://example.org/videos/foo.mp4").toURL()
        val path = PathUtil.toPath(root, url)
        assertEquals(PathUtil.fromPath(root, path), Some(url))

    /**
     * A path under the root that is too short to name a URL is simply not a cache entry. It has to answer None rather
     * than throw: `scanCache` walks everything under the cache root on startup, so a stray file someone dropped in
     * there would otherwise take the whole service down before it ever served a request.
     */
    test("fromPath returns None for paths that cannot name a URL"):
        val root = Paths.get("/Users/brian")
        assertEquals(PathUtil.fromPath(root, root), None, "the root itself names no video")
        assertEquals(PathUtil.fromPath(root, Paths.get("/Users/brian/stray.jpg")), None, "a host with no path")
        assertEquals(PathUtil.fromPath(root, Paths.get("/Users/kevin/elsewhere.jpg")), None, "not under the root")
