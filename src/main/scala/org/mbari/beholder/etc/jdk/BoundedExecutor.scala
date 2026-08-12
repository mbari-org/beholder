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

import java.time.Duration
import java.util.concurrent.{
    ArrayBlockingQueue,
    Executor,
    RejectedExecutionException,
    ThreadFactory,
    ThreadPoolExecutor,
    TimeUnit
}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import org.mbari.beholder.etc.jdk.Logging.given

import scala.concurrent.{Future, Promise}
import scala.util.Try

/**
 * A fixed pool with a bounded queue, for work that must not be allowed to pile up without limit.
 *
 * The alternative — `Future` on `ExecutionContext.global` — is a poor fit for shelling out to a subprocess. Its queue
 * is unbounded, so a burst is accepted in full and the clients at the back have long since timed out by the time their
 * work runs.
 *
 * Here, saturation is visible instead. `submit` returns `None` rather than queueing without limit, which lets the
 * caller shed load — an immediate "busy" is a better answer than a response nobody is waiting for any more.
 *
 * This is a real [[java.util.concurrent.Executor]], so it can be handed to anything that takes one —
 * `ExecutionContext.fromExecutor`, `CompletableFuture.supplyAsync`, and so on.
 *
 * `maxWait` allows the bounded queue to limit how much work is *accepted*; tasks are discarded if they wait too 
 * long for a worker rather than being run.
 *
 * @param name
 *   Prefix for the worker thread names, so stack dumps say which pool is busy
 * @param threads
 *   How many tasks may run at once
 * @param queueSize
 *   How many may wait
 * @param maxWait
 *   How long a task may sit in the queue before a worker discards it rather than running it. Applies to [[submit]] only
 *   — see [[execute]] for why. Zero or negative means no deadline: queued work always runs, however long it waited.
 */
class BoundedExecutor(
    name: String,
    threads: Int,
    queueSize: Int,
    maxWait: Duration = Duration.ZERO
) extends Executor:

    require(threads > 0, s"threads must be > 0. You used $threads")
    require(queueSize > 0, s"queueSize must be > 0. You used $queueSize")

    private val log = System.getLogger(getClass.getName)

    private val maxWaitNanos: Long = maxWait.toNanos

    private val staleDrops = AtomicLong(0)

    // System.nanoTime, not currentTimeMillis: it is monotonic, so a clock step cannot make a task
    // that was just submitted look like it has been waiting for hours.
    private def deadlineFromNow(): Long = System.nanoTime() + maxWaitNanos

    // Subtract-then-compare rather than `now >= deadline`, which is the overflow-safe form when
    // nanoTime is free to start anywhere in the long range.
    private def isStale(deadline: Long): Boolean = maxWaitNanos > 0 && System.nanoTime() - deadline >= 0

    private val threadFactory: ThreadFactory =
        val counter = AtomicInteger(0)
        (r: Runnable) =>
            val t = Thread(r, s"$name-${counter.incrementAndGet()}")
            t.setDaemon(true)
            t

    // Named `pool`, not `executor`: this class is the executor now, and `executor.execute` inside
    // an `execute` override reads like recursion.
    private val pool =
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
     * Run `command` on this pool, per the [[java.util.concurrent.Executor]] contract: no handle on the result, and
     * saturation reported by throwing rather than by a return value.
     *
     * A `command` that throws is left to the pool's usual handling — the worker dies and is replaced, and the throwable
     * reaches the thread's uncaught-exception handler. Callers that want the failure back, or that would rather shed
     * load than catch, should use [[submit]] instead.
     *
     * Note that `maxWait` is deliberately **not** applied here. Once accepted, a `command` always runs. Silently
     * dropping it would break every wrapper built on this interface in the worst possible way: `supplyAsync` and
     * `ExecutionContext.fromExecutor` both complete their result from inside the Runnable, so a Runnable that never
     * runs leaves them waiting forever. An `Executor` has no channel to report a late refusal on, and a hang is a far
     * worse answer than a slow success. [[submit]] holds the Promise itself, so it can shed the work and still tell
     * whoever is waiting — which is why the deadline lives there.
     *
     * @throws java.util.concurrent.RejectedExecutionException
     *   if every worker and queue slot is taken
     */
    override def execute(command: Runnable): Unit = pool.execute(command)

    /**
     * Run `body` on this pool.
     *
     * There are two ways this sheds load, and they are reported differently because they are known at different times.
     * A full queue is known immediately, so it comes back as `None`. Having waited past `maxWait` is only known once a
     * worker picks the task up, by which point the caller already holds a Future — so that arrives as a
     * [[BoundedExecutor.StaleWorkException]] on the Future. Both mean the same thing to a caller: the work did not run
     * and will not, because the service is over capacity.
     *
     * @return
     *   The eventual result, or None if the pool and its queue are both full. A `body` that throws fails the returned
     *   Future; it does not take the worker thread down with it.
     */
    def submit[A](body: => A): Option[Future[A]] =
        val promise  = Promise[A]()
        // Stamped here, on the submitting thread, so it measures the wait rather than the run.
        val deadline = deadlineFromNow()
        try
            execute: () =>
                if isStale(deadline) then
                    val dropped = staleDrops.incrementAndGet()
                    log.atDebug
                        .log(() =>
                            s"$name discarded work that waited longer than ${maxWait.toMillis}ms for a worker ($dropped so far)"
                        )
                    promise.tryFailure(
                        BoundedExecutor.StaleWorkException(
                            s"Waited longer than ${maxWait.toMillis}ms for a worker in the $name pool"
                        )
                    )
                else
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
    def queued: Int = pool.getQueue.size()

    /**
     * How many tasks have been discarded for waiting past `maxWait` instead of being run.
     *
     * Worth watching: a number that climbs says the queue is deeper than the service can drain within the deadline, so
     * either `threads` is too low for the arrival rate or `queueSize` is promising more than it can keep.
     */
    def droppedWhileWaiting: Long = staleDrops.get()

    def shutdown(): Unit = pool.shutdownNow()

object BoundedExecutor:

    /**
     * Fails the Future of a task that reached a worker only after its deadline had passed, so it was discarded unrun.
     *
     * A subclass of `RejectedExecutionException` because that is exactly what happened — the pool refused the work,
     * just later than usual. Callers that map rejection to a "busy, try again" response can therefore treat it the same
     * way they treat a `None` from [[BoundedExecutor.submit]], without caring which side of the queue the refusal came
     * from.
     */
    class StaleWorkException(message: String) extends RejectedExecutionException(message)
