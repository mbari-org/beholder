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

import java.net.URL
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.util.Using

object TestUtil:

    val bigBuckBunny: URL =
        val url = getClass.getResource("/Big_Buck_Bunny_1080_10s_1MB.mp4")
        // sbt 2 packs test resources into a JAR, giving a jar: URL that ffmpeg can't read.
        // Extract to a temp file so callers always get a file: URL.
        if url.getProtocol == "jar" then
            val tmp = Files.createTempFile("beholder_test_", ".mp4")
            tmp.toFile.deleteOnExit()
            Using(url.openStream()) { in =>
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse:
                throw new RuntimeException(s"Failed to extract test video from ${url.toString}")
            tmp.toUri.toURL
        else url

    val root: Path = Paths.get("target", "test_cache")
