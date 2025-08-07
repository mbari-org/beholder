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

object VectorUtil:

    /**
     * Fast way to remove a single item from an immutable list
     * @param idx
     *   The idx of the item to remove
     * @param src
     *   The list to remove it from
     * @return
     *   A list with the idx item removed. An exception is thrown if the idx is out of bounds of the list
     */
    def removeAtIdx[T](idx: Int, src: Vector[T]): Vector[T] =
        require(src.length > idx && idx >= 0)
        src.patch(idx, Nil, 1)