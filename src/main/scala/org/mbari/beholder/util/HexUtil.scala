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

object HexUtil:

  def toHex(bytes: Array[Byte]): String =
    val sb = new StringBuilder
    for (b <- bytes)
      sb.append(String.format("%02x", Byte.box(b)))
    sb.toString

  def fromHex(hex: String): Array[Byte] =
    hex
      .grouped(2)
      .toArray
      .map(i => Integer.parseInt(i, 16).toByte)
