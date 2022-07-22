/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder

import com.typesafe.config.ConfigFactory
import scala.util.Try

object AppConfig:

  val Config = ConfigFactory.load()

  val Name: String = "beholder"

  val Version: String =
    Try(getClass.getPackage.getImplementationVersion).getOrElse("0.0.0-SNAPSHOT")

  object Http:
    val Port: Int = Config.getInt("beholder.http.port")
