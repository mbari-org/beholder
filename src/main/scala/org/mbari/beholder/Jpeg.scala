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
import java.nio.file.{Files, Path, Paths}
import java.time.{Duration, Instant}
import org.mbari.beholder.etc.jdk.{DurationUtil, PathUtil}
import org.mbari.beholder.util.NumberUtil

/**
 * Information about the source of a JPEG
 * @param videoUrl
 *   The URL to the source video
 * @param elapsedTime
 *   The elapsed time into the video that the jpeg was taken
 * @param path
 *   The local path to the jpeg file
 * @param created
 *   When the jpeg was created. Used by the cache to determine which items to drop.
 * @param sizeBytes
 *   The size of the jpeg file in bytes
 */
case class Jpeg(
    videoUrl: URL,
    elapsedTime: Duration,
    path: Path,
    created: Instant = Instant.now(),
    sizeBytes: Option[Long] = None
):
  val sizeMB: Option[Double] = sizeBytes.map(NumberUtil.byteToMB)

object Jpeg:

  /**
   * Generates jpeg info
   * @param root
   *   The root directory of the cache
   * @param url
   *   The video url
   * @param elapsedTime
   *   The elapsed tie into the video
   * @return
   *   The jpeg info
   */
  def toPath(root: Path, url: URL, elapsedTime: Duration): Jpeg =
    val filename = DurationUtil.toHMS(elapsedTime).replace(":", "_") + ".jpg"
    val parent   = PathUtil.toPath(root, url)
    val path     = parent.resolve(filename)
    Jpeg(url, elapsedTime, path)

  /**
   * Generates Jpeg info
   * @param root
   *   The root directory of the cache
   * @param file
   *   the jpeg file (must be under the root fo the cache directory)
   * @return
   *   A jpeg info. None the file is not a jpeg or if it's not under the cache directory
   */
  def fromPath(root: Path, file: Path): Option[Jpeg] =
    if (!Files.isDirectory(file) && PathUtil.isChild(root, file) && PathUtil.isJpeg(file))
      val parent = file.getParent
      PathUtil
        .fromPath(root, parent)
        .map(videoUrl =>
          val filename    = PathUtil.dropExtension(file).replace("_", ":")
          val elapsedTime = DurationUtil.fromHMS(filename)
          val size        = if (Files.isRegularFile(file)) Some(Files.size(file)) else None
          Jpeg(videoUrl, elapsedTime, file, sizeBytes = size)
        )
    else None

  private val fakeUrl  = new URL("http://www.mbari.org")
  private val fakePath = Paths.get("/foo/bar.jpg")

  /**
   * Constructs a fake/mock jpeg that is useful for searchies
   * @param elapsedTime
   */
  def fake(elapsedTime: Duration): Jpeg     = Jpeg(fakeUrl, elapsedTime, fakePath)
  def fake(url: URL, elapsedTime: Duration) = Jpeg(url, elapsedTime, fakePath)
