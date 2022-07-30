/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.util

object NumberUtil:

  /**
   * This is the S.I. definition not b / 1000^2 Not the traditional version of b / 1024^2. The
   * traditional version is actually a mebibyte
   */
  def byteToMB(byte: Long): Double = byte / 1000000d

  def mbToByte(megaByte: Double): Long = math.round(megaByte * 1000000d)
