/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential.
 */

package org.mbari.beholder.util

object ListUtil:

  /**
   * Fast way to remove a single item from an immutable list
   * @param idx
   *   The idx of the item to remove
   * @param src
   *   The list to remove it from
   * @return
   *   A list with the idx item removed. An exception is thrown if the idx is out of bounds of the
   *   list
   */
  def removeAtIdx[T](idx: Int, src: List[T]): List[T] =
    require(src.length > idx && idx >= 0)
    val (left, _ :: right) = src.splitAt(idx)
    left ++ right
