/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.etc.jdk

import java.nio.file.Path
import java.net.URL
import java.nio.file.Paths
import org.mbari.beholder.etc.ffmpeg.DurationString.DurationString

object PathUtil:

  def isChild(parent: Path, child: Path): Boolean =
    val parentPath = parent.toAbsolutePath.normalize()
    val childPath  = child.toAbsolutePath.normalize()
    childPath.startsWith(parentPath)

  def isJpeg(path: Path): Boolean =
    val ext = path.getFileName.toString.toLowerCase
    ext.endsWith(".jpg") || ext.endsWith(".jpeg")

  def dropExtension(path: Path): String =
    val fileName = path.getFileName.toString
    val ext      = fileName.lastIndexOf('.')
    if (ext < 0) fileName
    else fileName.substring(0, ext)

  /**
   * Converts a URL to a local path. useful for creatign directories to save images into
   * {{{
   * val root = Paths.get("/Users/brian")
   * val u =  URL("http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4")
   * val p = PathUtil.toPath(root, u)
   * // /Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4
   * }}}
   */
  def toPath(root: Path, url: URL): Path =
    val host = url.getHost
    val path = url.getPath
    Paths.get(root.toString, host, path)

  /**
   * Converts a path to a URL. useful for recreating URLs from saved images
   * {{{
   * val root = Paths.get("/Users/brian")
   * val p = /Users/brian/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4
   * val u = PathUtil.toURL(root, p)
   * // http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1429/D1429_20220317T195416Z_h264.mp4
   * }}}
   */
  def fromPath(root: Path, path: Path): Option[URL] =
    if (isChild(root, path))
      val raw     = root.relativize(path)
      val host    = raw.subpath(0, 1).toString
      val urlPath = raw.subpath(1, raw.getNameCount).toString
      Some(new URL(s"http://$host/$urlPath"))
    else None
