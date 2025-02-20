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

package org.mbari.beholder.etc.zio

import zio.*
import org.mbari.beholder.etc.jdk.Logging.given
import zio.Cause.Die

object ZioUtil:

    private val log = java.lang.System.getLogger(getClass.getName)

    /**
     * Run the given effect. Throws an exception if the effect fails.
     *
     * @param app
     *   The effect to run
     * @return
     *   The result of the effect
     */
    def unsafeRun[E, A](app: ZIO[Any, E, A]): A =
        Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(app).getOrThrowFiberFailure()
        }

    /**
     * Run the given effect.
     *
     * @param app
     *   The effect to run
     * @return
     *   The result of the effect. Some on success. None on Failure
     */
    def safeRun[E, A](app: ZIO[Any, E, A]): Option[A] =
        Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe.run(app) match
                case Exit.Success(a) => Some(a)
                case Exit.Failure(e) =>
                    e match
                        case d: Die => log.atError.withCause(d.value).log(e.toString)
                        case _      => log.atError.log(e.toString)
                    None
        }
