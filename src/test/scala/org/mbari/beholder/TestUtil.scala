/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder

import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

object TestUtil:

  val bigBuckBunny: URL = getClass.getResource("/Big_Buck_Bunny_1080_10s_1MB.mp4")

  val root: Path = Paths.get("target", "test_cache")
