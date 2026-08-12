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

import java.util.concurrent.{CountDownLatch, Executor, RejectedExecutionException, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration as ScalaDuration
import scala.util.Try

class BoundedExecutorSuite extends munit.FunSuite:

    private val patience = ScalaDuration(10, TimeUnit.SECONDS)

    /** One worker, room for one more task: the third submission has nowhere to go. */
    private def oneAtATime(): BoundedExecutor = BoundedExecutor("test", threads = 1, queueSize = 1)

    test("is an Executor, so it can be handed to anything that takes one"):
        val executor = oneAtATime()
        val ran      = CountDownLatch(1)
        try
            val asExecutor: Executor = executor // must compile: it *is* one, not merely shaped like one
            asExecutor.execute(() => ran.countDown())
            assert(ran.await(10, TimeUnit.SECONDS), "the command never ran")
        finally executor.shutdown()

    test("works as an ExecutionContext, so Futures can be run on the bounded pool"):
        val executor = oneAtATime()
        try
            given ExecutionContext = ExecutionContext.fromExecutor(executor)
            assertEquals(Await.result(Future(21 * 2), patience), 42)
        finally executor.shutdown()

    /**
     * `submit` answers saturation with `None`, but `Executor.execute` has no return value to say it with — the contract
     * is to throw. Callers picking the plain `execute` path need that signal to survive.
     */
    test("execute throws once every worker and queue slot is taken"):
        val executor = oneAtATime()
        val started  = CountDownLatch(1)
        val release  = CountDownLatch(1)
        val finished = CountDownLatch(1)
        try
            // Occupies the only worker thread.
            executor.execute: () =>
                started.countDown()
                release.await()
                finished.countDown()
            assert(started.await(10, TimeUnit.SECONDS), "the first command never started")

            executor.execute(() => ()) // fills the only queue slot
            intercept[RejectedExecutionException](executor.execute(() => ()))

            // Let the blocked command end on its own. `shutdownNow` would interrupt it instead, and
            // an interrupt out of a bare `execute` has nowhere to go but the uncaught-exception
            // handler — a stack trace on stderr that looks like a test failure but isn't.
            release.countDown()
            assert(finished.await(10, TimeUnit.SECONDS), "the first command never finished")
        finally
            release.countDown()
            executor.shutdown()

    test("submit runs the body and completes the future with its result"):
        val executor = oneAtATime()
        try
            val result = executor.submit(21 * 2).getOrElse(fail("submit should have accepted the work"))
            assertEquals(Await.result(result, patience), 42)
        finally executor.shutdown()

    test("submit fails the future when the body throws, instead of losing the error"):
        val executor = oneAtATime()
        try
            val result = executor
                .submit(throw new IllegalStateException("boom"))
                .getOrElse(fail("submit should have accepted the work"))
            val thrown = Try(Await.result(result, patience)).failed.get
            assertEquals(thrown.getMessage, "boom")
        finally executor.shutdown()

    /**
     * `Try` treats InterruptedException as fatal and rethrows it, so a worker interrupted at shutdown will not complete
     * its Promise unless the executor handles that case itself. The caller is an in-flight HTTP request; it has to be
     * told the work died rather than be left waiting on a Future that can never complete.
     */
    test("submit fails the future when its worker is interrupted, rather than leaving the caller waiting"):
        val executor = oneAtATime()
        val started  = CountDownLatch(1)
        val forever  = CountDownLatch(1)

        val blocked = executor
            .submit { started.countDown(); forever.await() }
            .getOrElse(fail("submit should have accepted the work"))
        assert(started.await(10, TimeUnit.SECONDS), "the task never started")

        executor.shutdown() // interrupts the worker

        // Scala boxes InterruptedException in an ExecutionException on its way into a Promise,
        // so the interruption shows up as the cause rather than the exception itself.
        val thrown = Try(Await.result(blocked, patience)).failed.get
        val reason = Option(thrown.getCause).getOrElse(thrown)
        assert(
            reason.isInstanceOf[InterruptedException],
            s"Expected the future to fail because of an interruption, got $thrown"
        )

    test("submit returns None once every worker and queue slot is taken"):
        val executor = oneAtATime()
        val started  = CountDownLatch(1)
        val release  = CountDownLatch(1)
        try
            // Occupies the only worker thread.
            executor
                .submit { started.countDown(); release.await() }
                .getOrElse(fail("the first submission should have been accepted"))
            assert(started.await(10, TimeUnit.SECONDS), "the first task never started")

            // Fills the only queue slot.
            assert(executor.submit(()).isDefined, "the second submission should have been queued")

            // Nowhere left to put it.
            assertEquals(executor.submit(()), None)
        finally
            release.countDown()
            executor.shutdown()

    test("submit accepts work again once the queue drains"):
        val executor = oneAtATime()
        val started  = CountDownLatch(1)
        val release  = CountDownLatch(1)
        try
            executor
                .submit { started.countDown(); release.await() }
                .getOrElse(fail("the first submission should have been accepted"))
            assert(started.await(10, TimeUnit.SECONDS), "the first task never started")

            val queued = executor.submit(()).getOrElse(fail("the second submission should have been queued"))
            assertEquals(executor.submit(()), None)

            release.countDown()
            Await.result(queued, patience)

            assert(executor.submit(()).isDefined, "submit should accept work again after the queue drains")
        finally
            release.countDown()
            executor.shutdown()
