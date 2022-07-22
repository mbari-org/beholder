/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder.etc.zio

import zio.{Unsafe, ZIO}

object ZioUtil:

  /**
   * Runs a zio app using the default runtime. Throws an 
   * exception if something bombs.
   */
  def unsafeRun[E, A](app: ZIO[Any, E, A]): A =
    Unsafe.unsafe(zio.Runtime.default.unsafe.run(app).getOrThrowFiberFailure())
