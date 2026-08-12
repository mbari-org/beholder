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

import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.TimeoutException

class ProcessRunnerSuite extends munit.FunSuite:

    private val generous = Duration.ofSeconds(30)

    test("run returns the exit code and stdout of a command that completes"):
        ProcessRunner.run(Seq("sh", "-c", "printf hello"), generous) match
            case Left(e)       => fail(s"Expected a result, got: ${e.getMessage}")
            case Right(result) =>
                assertEquals(result.exitCode, 0)
                assertEquals(result.stdout.trim, "hello")

    test("run captures stderr and a non-zero exit code"):
        ProcessRunner.run(Seq("sh", "-c", "printf oops >&2; exit 3"), generous) match
            case Left(e)       => fail(s"Expected a result, got: ${e.getMessage}")
            case Right(result) =>
                assertEquals(result.exitCode, 3)
                assertEquals(result.stderr.trim, "oops")

    test("run returns Left when the executable cannot be started"):
        val result = ProcessRunner.run(Seq("beholder-no-such-executable-xyz"), generous)
        assert(result.isLeft, "Expected a Left when the executable does not exist")

    test("run returns a TimeoutException when the process outlives the timeout"):
        val start   = System.currentTimeMillis()
        val result  = ProcessRunner.run(Seq("sleep", "30"), Duration.ofMillis(250))
        val elapsed = System.currentTimeMillis() - start

        result match
            case Right(r) => fail(s"Expected a timeout, but the process returned exit code ${r.exitCode}")
            case Left(e)  => assert(e.isInstanceOf[TimeoutException], s"Expected a TimeoutException, got $e")

        assert(elapsed < 10000, s"run waited ${elapsed}ms; it should have given up near the 250ms timeout")

    /**
     * The point of the timeout is that the process is actually gone, not merely abandoned. A shell that survived the
     * kill would go on to create the sentinel file.
     */
    test("run kills the timed-out process so its remaining work never happens"):
        val sentinel: Path = Files.createTempDirectory("beholder-runner-").resolve("sentinel.txt")
        try
            val result = ProcessRunner.run(
                Seq("sh", "-c", s"sleep 1; printf survived > ${sentinel.toString}"),
                Duration.ofMillis(200)
            )
            assert(result.isLeft, "Expected the run to time out")

            Thread.sleep(2500) // outlast the subprocess's sleep
            assert(!Files.exists(sentinel), s"Process survived the timeout and wrote $sentinel")
        finally
            Files.deleteIfExists(sentinel)
            Files.deleteIfExists(sentinel.getParent)
