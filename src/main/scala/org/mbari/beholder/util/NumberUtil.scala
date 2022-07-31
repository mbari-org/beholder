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

package org.mbari.beholder.util

object NumberUtil:

  /**
   * This is the S.I. definition not b / 1000^2 Not the traditional version of b / 1024^2. The
   * traditional version is actually a mebibyte
   */
  def byteToMB(byte: Long): Double = byte / 1000000d

  def mbToByte(megaByte: Double): Long = math.round(megaByte * 1000000d)
