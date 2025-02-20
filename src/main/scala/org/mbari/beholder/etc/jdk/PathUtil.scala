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

import java.nio.file.Path
import java.net.URL
import java.nio.file.Paths
import org.mbari.beholder.etc.ffmpeg.DurationString.DurationString
import java.nio.file.Files
import java.net.URI

object PathUtil:

    /**
     * Checks if a path is a child of another path
     * @param parent
     *   The parent directory
     * @param child
     *   A child directory of file
     * @return
     *   true if child is contained under parent
     */
    def isChild(parent: Path, child: Path): Boolean =
        val parentPath = parent.toAbsolutePath.normalize()
        val childPath  = child.toAbsolutePath.normalize()
        childPath.startsWith(parentPath)

    /**
     * Simple check of a files extension
     * @param path
     *   The file to check
     * @return
     *   true if the path is a file and it has a .jpg or .jpeg extension
     */
    def isJpeg(path: Path): Boolean =
        if Files.isDirectory(path) then false
        else
            val ext = path.getFileName.toString.toLowerCase
            ext.endsWith(".jpg") || ext.endsWith(".jpeg")

    /**
     * Grabs the filename without an extension from a path
     * @param path
     *   The path to parse
     * @return
     *   The filename without an extension. If the path is a directory an empty string is returned
     */
    def dropExtension(path: Path): String =
        if Files.isDirectory(path) then ""
        else
            val fileName = path.getFileName.toString
            val ext      = fileName.lastIndexOf('.')
            if ext < 0 then fileName
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
        Paths
            .get(root.toString, host, path)
            .toAbsolutePath()
            .normalize()

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
        if isChild(root, path) then
            val raw     = root.relativize(path)
            val host    = raw.subpath(0, 1).toString
            val urlPath = raw.subpath(1, raw.getNameCount).toString
            Some(URI.create(s"http://$host/$urlPath").toURL)
        else None
