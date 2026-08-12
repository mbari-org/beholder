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
import java.util.concurrent.{TimeUnit, TimeoutException}
import org.mbari.beholder.etc.jdk.Logging.given

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/**
 * What a subprocess did once it exited on its own. A non-zero `exitCode` is still a `ProcessResult` — the process ran
 * and reported failure. Only a process that never started, or one that had to be killed, comes back as a `Left` from
 * [[ProcessRunner.run]].
 */
final case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

/**
 * Runs an external command with a hard upper bound on how long it may take.
 *
 * `scala.sys.process` is not used here for two reasons. It has no timed wait — `Process(cmd).!` blocks until the
 * process exits, so a stalled ffmpeg (a video URL whose origin server never responds, say) pins the calling thread
 * forever. And it spawns a daemon thread per stream to pump stdout/stderr, which at capture concurrency is three extra
 * threads per frame. Redirecting both streams to temp files avoids the pump threads entirely and, more importantly,
 * avoids the pipe-buffer deadlock you would otherwise get from waiting on a process while nothing is draining its
 * output.
 */
object ProcessRunner:

    private val log = System.getLogger(getClass.getName())

    /**
     * Run `cmd`, waiting at most `timeout` for it to finish.
     *
     * @return
     *   Right with the exit code and captured output if the process exited on its own; Left if it could not be started,
     *   or a `TimeoutException` if it overran `timeout` and had to be killed.
     */
    def run(cmd: Seq[String], timeout: Duration): Either[Throwable, ProcessResult] =
        var stdoutFile: Path = null
        var stderrFile: Path = null
        try
            stdoutFile = Files.createTempFile("beholder-proc-", ".out")
            stderrFile = Files.createTempFile("beholder-proc-", ".err")

            val process = new ProcessBuilder(cmd.asJava)
                .redirectOutput(stdoutFile.toFile)
                .redirectError(stderrFile.toFile)
                .start()

            // Nothing is ever written to the child's stdin; leaving it open would make a
            // process that reads stdin wait forever instead of seeing EOF.
            process.getOutputStream.close()

            if process.waitFor(timeout.toMillis, TimeUnit.MILLISECONDS) then
                Right(ProcessResult(process.exitValue(), read(stdoutFile), read(stderrFile)))
            else
                process.destroyForcibly()
                // Reap it so the JVM does not leave a zombie behind. SIGKILL is not refusable,
                // so this returns promptly.
                process.waitFor(5, TimeUnit.SECONDS)
                log.atWarn.log(() => s"Killed after ${timeout.toMillis} ms: ${cmd.mkString(" ")}")
                Left(
                    new TimeoutException(
                        s"Command exceeded its ${timeout.toMillis} ms timeout and was killed: ${cmd.mkString(" ")}"
                    )
                )
        catch case NonFatal(e) => Left(e)
        finally
            deleteQuietly(stdoutFile)
            deleteQuietly(stderrFile)

    private def read(path: Path): String =
        try Files.readString(path)
        catch case NonFatal(_) => ""

    private def deleteQuietly(path: Path): Unit =
        if path != null then
            try Files.deleteIfExists(path)
            catch case NonFatal(_) => ()
