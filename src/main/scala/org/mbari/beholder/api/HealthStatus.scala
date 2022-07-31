/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.api

import org.mbari.beholder.AppConfig

final case class HealthStatus(
    jdkVersion: String,
    availableProcessors: Int,
    freeMemory: Long,
    maxMemory: Long,
    totalMemory: Long,
    application: String = AppConfig.Name,
    version: String = AppConfig.Version,
    description: String = AppConfig.Description
)

object HealthStatus:

  /**
   * @return
   *   a HealthStatus object with the current JVM stats
   */
  def default: HealthStatus =
    val runtime = Runtime.getRuntime
    HealthStatus(
      jdkVersion = Runtime.version.toString,
      availableProcessors = runtime.availableProcessors,
      freeMemory = runtime.freeMemory,
      maxMemory = runtime.maxMemory,
      totalMemory = runtime.totalMemory
    )

  def empty(application: String): HealthStatus =
    HealthStatus(
      jdkVersion = "",
      availableProcessors = 0,
      freeMemory = 0,
      maxMemory = 0,
      totalMemory = 0,
      application = application,
      version = "0.0.0",
      description = ""
    )
