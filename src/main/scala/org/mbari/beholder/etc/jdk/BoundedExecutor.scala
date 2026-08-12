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

package org.mbari.beholder.etc.jdk

import java.util.concurrent.{
    ArrayBlockingQueue,
    RejectedExecutionException,
    ThreadFactory,
    ThreadPoolExecutor,
    TimeUnit
}
import java.util.concurrent.atomic.AtomicInteger
import org.mbari.beholder.etc.jdk.Logging.given

import scala.concurrent.{Future, Promise}
import scala.util.Try

/**
 * A fixed pool with a bounded queue, for work that must not be allowed to pile up without limit.
 *
 * The alternative — `Future` on `ExecutionContext.global` — is a poor fit for shelling out to a subprocess. Its queue
 * is unbounded, so a burst is accepted in full and the clients at the back have long since timed out by the time their
 * work runs; and because the pool is shared, that backlog also delays anything else the service wanted to do (a health
 * check, say). Blocking a worker there is invisible to the pool as well: `ForkJoinPool` only grows a compensation
 * thread for code that announces itself with `scala.concurrent.blocking`, which `scala.sys.process` does not.
 *
 * Here, saturation is visible instead. `submit` returns `None` rather than queueing without limit, which lets the
 * caller shed load — an immediate "busy" is a better answer than a response nobody is waiting for any more.
 *
 * @param name
 *   Prefix for the worker thread names, so stack dumps say which pool is busy
 * @param threads
 *   How many tasks may run at once
 * @param queueSize
 *   How many may wait
 */
class BoundedExecutor(name: String, threads: Int, queueSize: Int):

    require(threads > 0, s"threads must be > 0. You used $threads")
    require(queueSize > 0, s"queueSize must be > 0. You used $queueSize")

    private val log = System.getLogger(getClass.getName)

    private val threadFactory: ThreadFactory =
        val counter = AtomicInteger(0)
        (r: Runnable) =>
            val t = Thread(r, s"$name-${counter.incrementAndGet()}")
            t.setDaemon(true)
            t

    private val executor =
        // Core == max, so the pool never grows past `threads` and the queue is what absorbs bursts.
        ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue[Runnable](queueSize),
            threadFactory,
            ThreadPoolExecutor.AbortPolicy()
        )

    /**
     * Run `body` on this pool.
     *
     * @return
     *   The eventual result, or None if the pool and its queue are both full. A `body` that throws fails the returned
     *   Future; it does not take the worker thread down with it.
     */
    def submit[A](body: => A): Option[Future[A]] =
        val promise = Promise[A]()
        try
            executor.execute: () =>
                try promise.complete(Try(body))
                catch
                    // Try rethrows anything NonFatal declines to catch, InterruptedException
                    // included, which would leave the promise — and whoever is waiting on it —
                    // hanging. Interruption is normal at shutdown, so report it and let the pool
                    // see the flag; anything else is genuinely fatal and still propagates.
                    case t: InterruptedException =>
                        promise.tryFailure(t)
                        Thread.currentThread().interrupt()
                    case t: Throwable            =>
                        promise.tryFailure(t)
                        throw t
            Some(promise.future)
        catch
            case _: RejectedExecutionException =>
                log.atDebug.log(() => s"$name pool is saturated ($threads running, $queueSize queued); shedding work")
                None

    /** How many tasks are waiting for a worker. Approximate — the queue moves while you read it. */
    def queued: Int = executor.getQueue.size()

    def shutdown(): Unit = executor.shutdownNow()
