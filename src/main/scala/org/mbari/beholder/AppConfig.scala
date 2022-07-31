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

import com.typesafe.config.ConfigFactory
import scala.util.Try

object AppConfig:

  val Config = ConfigFactory.load()

  val Name: String = "beholder"

  val Description: String = "Framegrab server"

  val Version: String =
    Try(getClass.getPackage.getImplementationVersion).getOrElse("0.0.0-SNAPSHOT")

  object Api:
    val Key: String = Config.getString("beholder.api.key")

  object Http:
    val Port: Int = Config.getInt("beholder.http.port")

  object Cache:
    val sizeMb: Int     = Config.getInt("beholder.cache.size")
    val freePct: Double = Config.getDouble("beholder.cache.freepct")
