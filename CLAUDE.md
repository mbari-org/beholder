# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Beholder is a Scala 3 HTTP microservice that extracts individual frames from videos as JPEGs using ffmpeg. It accepts POST requests specifying a video URL and elapsed time, returning a cached JPEG frame.

**Prerequisite:** `ffmpeg` and `ffprobe` must be installed locally for the service to function. (Both ship in the same package on Homebrew and Debian/Ubuntu.) Without `ffprobe`, captures still succeed but H.264/HEVC frames keep their codec padding rows — see `FfprobeUtil`.

## Build & Development Commands

All commands run inside `sbt`:

```bash
sbt compile          # Compile
sbt test             # Incremental in sbt 2 — only runs suites affected by changes (delegates to testQuick)
sbt "Test/testOnly org.mbari.beholder.*"  # Force ALL 124 tests to run
sbt "testOnly org.mbari.beholder.ImageCacheImplSuite"  # Run a single test suite
sbt run              # Run (requires <cacheRoot> argument via picocli)
sbt stage            # Build runnable project to target/out/jvm/<scala>/beholder/universal/stage/
                     # NOT target/universal/stage — that is a stale sbt 1 leftover that never refreshes
sbt scalafmtAll      # Format all code (run after compile to use Scala 3 indent-based style)
sbt docker:publishLocal  # Build Docker image locally
sbt laikaSite        # Build documentation to target/docs/site/
```

## Architecture

### Request Flow

`POST /capture` → `CaptureEndpoints` → `JpegCapture` → `JpegCache` (hit) or `FfmpegUtil` (miss) → JPEG file response

### Key Components

- **`Main`** — Entry point using picocli. Wires up Vert.x HTTP server with Tapir endpoints. Accepts `<cacheRoot>` as a required positional arg, plus optional flags for port, cache size, free percent, and API key.
- **`AppConfig`** — Reads Typesafe Config (`reference.conf`). All settings overridable via environment variables.
- **`JpegCapture`** — Orchestrates capture: checks cache first, calls ffmpeg on miss.
- **`JpegCache`** — In-memory concurrent cache (`ConcurrentHashMap[URL, Vector[Jpeg]]`) backed by disk. Auto-scans disk on startup to rebuild state after restarts. Evicts least-recently-used entries when disk usage exceeds limit: every cache hit `touch`es its entry, refreshing `created` (in memory only — after a restart, ages come from file creation times).
- **`Jpeg`** — Data model for a cached frame. Cache path is derived from video URL + elapsed time: `<root>/<host>/<url-path>/<hh_mm_ss.sss>.jpg`, with a `_deinterlaced` suffix on the stem when a deinterlacer was run.
- **`FfmpegUtil`** — Shells out to `ffmpeg` for frame extraction. Supports accurate (slow) vs. keyframe (fast) seeking modes, and optional deinterlacing. It takes `deinterlace` **literally** — deciding whether a capture should be deinterlaced is `ImageCapture`'s job, because that same decision names the cache file.
- **`Ffprobe`** — Trait for inspecting a video before capture. Returns a `VideoInfo` (size + `FieldOrder`) from a single call. Needed because `FfmpegUtil` passes `-apply_cropping 0` to ignore MOV clean-aperture (clap) atoms, which also disables the codec's own crop; the probed size is used to crop off H.264/HEVC macroblock padding (the green band at 1088 instead of 1080). The field order is what drives deinterlacing — see "Deinterlacing" below.
- **`FfprobeUtil`** — `Ffprobe` implementation that shells out to `ffprobe`. Uncached — every call runs the process. Degrades gracefully if `ffprobe` is missing (warns once, returns `None`).
- **`FfprobeService`** — `Ffprobe` implementation that wraps another one in a size-bounded Caffeine cache. `FfprobeService.default` is what `FfmpegUtil` uses. Concurrent lookups of the same URI share a single probe; failed probes are not cached, so they retry.
- **`ProcessRunner`** (`etc/jdk/`) — Runs an external command with a hard timeout, killing it if it overruns. Used by both `FfmpegUtil` and `FfprobeUtil`. Deliberately *not* `scala.sys.process`: that has no timed wait (`Process(cmd).!` blocks forever on a stalled video URL) and spawns a daemon thread per stream. Streams are redirected to temp files, which avoids both the pump threads and the pipe-buffer deadlock.
- **`BoundedExecutor`** (`etc/jdk/`) — Fixed pool + bounded queue for capture work. Implements `java.util.concurrent.Executor` (so it works with `ExecutionContext.fromExecutor` and friends), but not `ExecutorService` — those `submit` overloads would collide with its own by-name `submit`. Two ways in: `execute` throws `RejectedExecutionException` when saturated, per the interface contract; `submit` returns `None` instead, which is what lets the capture path shed load rather than queue without limit.

### Concurrency model

Captures run on `BoundedExecutor`, **not** `ExecutionContext.global`. This matters:

- ffmpeg is an external process and its thread is held for the whole capture. `ForkJoinPool` only grows a compensation thread for code that announces itself via `scala.concurrent.blocking`, which `scala.sys.process` does not — so on the global pool, concurrency was silently capped at `availableProcessors` with an unbounded queue behind it.
- The pool default is `max(2, cores/4)`, not `cores`: ffmpeg is internally multi-threaded, so one process per core oversubscribes badly. Each process's memory also lives outside the JVM heap, where `-Xmx` won't protect you but the container cgroup OOM-killer will.
- Past the queue, `/capture*` answers **503** (`ServiceUnavailable`) rather than accepting work whose client has already timed out. The API key is checked *before* a pool slot is claimed.
- Being *in* the queue is not a promise of being run. Each `submit` is stamped with a deadline (`BEHOLDER_CAPTURE_MAXWAIT`, 15s) and a worker discards it unrun if it waited longer. See "Queue-wait deadline" below.
- Keeping captures off the global pool is also what keeps `/health` responsive under load — it previously queued behind every in-flight capture, so a busy service looked dead to an orchestrator.

Note `tapir`'s `blockingRoute` (used in `Main`) does **not** throttle any of this: its handler builds the Future and discards it, so the Vert.x worker returns immediately.

### Queue-wait deadline

Nothing in this service cancels a queued capture — there is no `cancel`, no `closeHandler`, no disconnect hook anywhere in `src/main/scala`. So a request whose client gave up at 30s still costs a **full ffmpeg run** when it reaches the front of the queue, while live requests wait behind it. Deep enough, that is self-sustaining: the pool spends all its time producing frames for closed sockets.

`BoundedExecutor(maxWait = ...)` stamps each `submit` with `System.nanoTime() + maxWait` and a worker checks it before running the body. Stale work then costs a clock comparison instead of a subprocess.

Sizing note: the queue's real limit is *time*, not depth. Drain time for the last slot is `queueSize / threads × captureTime` — at the auto defaults on a 24-core box (6 threads, 48 deep) that is ~16s at 2s/capture. `maxWait` is what makes a deeper `queueSize` safe, so raise them together, and keep `maxWait` under the timeout your clients use.

Three things to preserve:

- The deadline applies to `submit`, **not** `execute`. `execute` must honor the `Executor` contract: `CompletableFuture.supplyAsync` and `ExecutionContext.fromExecutor` both complete their result from inside the Runnable, so silently dropping one hangs the caller forever. `submit` holds the `Promise`, so it can shed the work *and* say so.
- The stamp is taken on the submitting thread, before `execute`, so it measures queue wait rather than run time.
- `StaleWorkException` extends `RejectedExecutionException` on purpose — a late refusal is still a refusal, so `CaptureEndpoints` maps it to the same 503 as a full queue (different message, since the two imply different fixes). Make it a bare `Exception` and callers start returning 500 for an overload condition.

Both 503s carry a **jittered `Retry-After`**, built by `ServiceUnavailable.busy` and projected into the header by `serviceUnavailableOut` in `Endpoints.scala`. Two things not to undo:

- The value must stay random within `MinRetryAfterSeconds`..`MaxRetryAfterSeconds` (2–8). A constant sends every client shed by one burst back at the same instant, rebuilding the burst in phase with itself — so it can repeat indefinitely rather than decay.
- The jitter lives in `busy`, **not** in a default argument on the `ServiceUnavailable` constructor. A random default would make two otherwise identical values compare unequal and quietly break case-class equality everywhere. `ServiceUnavailableSuite` pins both properties.

`droppedWhileWaiting` counts the drops. A number that climbs means the queue promises more than the pool can drain inside the deadline — raise `threads` or lower `queueSize`.

### Request coalescing (single-flight)

`ImageCapture` keeps a `ConcurrentHashMap[Path, CompletableFuture[...]]` of in-flight captures. Concurrent requests for the same frame run ffmpeg **once**; the rest wait on that result. Verified end-to-end: 40 simultaneous requests for one uncached frame peak at 1 ffmpeg process, while 40 requests for distinct frames peak at 6 (the pool size).

This is a correctness fix as much as a throughput one. The target path is derived from the request, not made unique, so without coalescing N ffmpeg processes write the same file concurrently (`-y`) and a caller can be served a half-written frame.

Note the coalescing happens *behind* the pool, not in front of it: `captureLogic` submits, and the join runs on a worker. So 40 requests for one frame take 40 slots — 1 running ffmpeg, 39 parked on `existing.join()`. They drain almost instantly once the leader finishes, so they are cheap in time but not in slots.

Two things to preserve if you touch it:

- The key is the cache key (uri, elapsed, imageType, effective deinterlace) — **not** `accurate`/`skipNonKeyFrames`. The cache isn't keyed on those either, so a request differing only in those flags already receives whichever frame was cached first. Coalescing on the same identity keeps the two consistent. Deinterlacing *is* in the key because it changes the pixels, and it lands there for free: the key is the target path, which the flag already names.
- `work` runs *outside* the map, never inside `computeIfAbsent`. That method holds a bin lock for the duration of its mapping function, which would stall unrelated captures hashing to the same bin for as long as an ffmpeg run.

`ImageCapture` takes an injectable `FrameGrabber` (defaulting to `FfmpegUtil.frameCapture`) so this is testable without real ffmpeg runs — same pattern as `FfprobeService` taking its delegate.

### Eviction and stale index entries

`ImageCacheImpl.freeDisk` removes evicted entries from the index **synchronously** but deletes their files **asynchronously** on `ImageCacheImpl.defaultDeleter` (one daemon thread). An eviction pass at production sizes is tens of thousands of deletes, and `freeDisk` runs on whichever capture worker called `put` — inline, that took a capture slot out of service for the whole pass.

The gap this opens is the bug it used to cause: cache paths are derived from the request, so a frame recaptured after eviction is written to the *same path* its pending delete targets. Deleting by path alone destroyed the fresh file while the index kept advertising it, and every later hit was a 500 (`FileNotFoundException` from Vert.x `sendFile`) until a restart. Two guards, both needed:

- **`deleteFromDisk` skips a re-adopted key.** The check and the unlink run inside `compute` on the inner map, which holds the same bin lock `put` takes — so no `put` can land between them. `ImageCacheImplSuite` reproduces the sequence with a manual executor; verified to fail with a delete-by-path.
- **`ImageCapture.findInCache` treats a missing file as a miss.** It drops the entry with `removeIfCurrent` (two-arg `remove`, so a concurrent fresh entry survives) and the caller recaptures. This covers what the first guard can't see — ffmpeg finished but not yet `put` — and files deleted from outside the process. Logged at WARN, so it's visible in production; a steady stream means something is still deleting under the cache.

Recency is the third piece, and it changes *which* frames get evicted, not whether eviction is safe. `ImageCapture` `touch`es every hit that passes the existence check, so a frame in constant demand is no longer evicted just for being an early capture — and then recaptured mid-pass, the riskiest moment. Don't mistake it for a fix to the race above: a least-recently-used frame can still be requested during a pass. Two invariants keep it consistent with eviction:

- **`touch` is conditional** (`replace(key, old, new)`). An unconditional `put` would resurrect an entry eviction already took, whose file may be about to go.
- **`freeDisk` removes with `remove(key, j)`, not `remove(key)`.** A touch swaps the index before the queue, so eviction can poll an entry that has just been replaced. That element is skipped: the replacement owns the file, and `put`/`touch` already settled the bytes. Removing by key would evict the fresh entry from the index while leaving it in the queue.

### Deinterlacing

`deinterlace` in the request body (default false) asks for interlaced source video to be deinterlaced. It is a *request*: `ImageCapture.resolveDeinterlace` turns it into what actually happens, and that resolved boolean is the only one anything downstream sees.

```
effective = requested && !skipNonKeyFrames && ffprobe.isInterlaced(uri)
```

- **`nokey` wins.** Under `-skip_frame nokey` the decoder emits only keyframes, so the frames bwdif compares are whole GOPs apart and its motion detection has nothing coherent to work with. The combination isn't useful, so the flag is dropped (logged at debug).
- **Checked before the probe.** A request that can't be deinterlaced never pays for an ffprobe call. `deinterlace=false` costs nothing new at all.
- **Unprobeable ⇒ false.** Deinterlacing a progressive frame visibly softens it, so when we can't tell, the cheaper mistake is to leave it alone. Same for `field_order=unknown`.

The filter is `bwdif=mode=send_frame:parity=auto:deint=all`, inserted **after** the crop and **before** the colorspace override. Three things not to undo:

- **bwdif, not yadif.** We decode one frame per capture, so the filter runs once and its cost vanishes next to the seek and decode. There's no quality/speed trade left to make, so take the better filter.
- **`mode=send_frame`.** bwdif defaults to `send_field`, which emits one frame per *field* — double the filtering, for a frame at the first field's instant rather than the frame's.
- **Crop first.** Keeps bwdif from interpolating against the macroblock padding rows. Safe because the crop offset is `0:0`, so field parity can't shift.

`-frames:v 1` is **correct as-is** and needs no adjustment. It caps frames at the muxer, not the decoder, so ffmpeg keeps feeding the filtergraph until bwdif — a delay filter that emits frame N only after seeing N+1 — can produce one. Measured: 8 frames enter bwdif, 6 leave, 1 is written, and the result is byte-identical to the same frame from an unconstrained `-frames:v 5` run.

**Deinterlaced frames are separate cache entries**, named `<hh_mm_ss.sss>_deinterlaced.<ext>`. Keying on the *effective* flag rather than the requested one is what keeps that honest: a progressive video asked for with `deinterlace=true` produces identical pixels to an ordinary capture, so it shares that entry instead of duplicating it.

Two traps this creates:

- `CachedImage.fromPath` must strip the suffix **before** turning underscores into colons. Leave it on and `00_00_01.234_deinterlaced` becomes `00:00:01.234:deinterlaced`, which `DurationUtil.fromHMS` reads the first three parts of and parses quite happily — so on the next restart a deinterlaced frame is silently adopted as an ordinary one instead of failing loudly.
- `ImageCacheImpl.evictionOrdering` must include the flag. It's a `ConcurrentSkipListSet`, so the comparator doubles as equality: if a frame and its deinterlaced twin compare 0, the second `add()` is silently dropped and that file is never evicted. Ties are real — `scanCache` restores `created` from filesystem timestamps, which are far coarser than the gap between two captures. `ImageCacheImplSuite` pins this directly on the comparator, because going through `put` (which stamps `Instant.now()`) essentially never produces a tie.

`src/test/resources/Interlaced_720x480_1s.mp4` is a genuinely interlaced fixture (`field_order=tt`, short GOP so `nokey` still finds a keyframe); `TestUtil.interlaced` documents how it was built. `FfmpegUtilSuite` captures the same frame with and without the filter and asserts the vertical-combing metric actually drops — verified to fail if the filter is disabled.

### API Layer (`src/main/scala/.../api/`)

- **`Endpoints`** — Base trait defining `baseEndpoint` with standard error variants (404, 500, 401).
- **`CaptureEndpoints`** — The main `/capture` POST endpoint. Auth via `X-Api-Key` header.
- **`HealthEndpoints`** — `/health` endpoint.
- **`SwaggerEndpoints`** — Auto-generated Swagger UI from Tapir endpoint definitions.

### Supporting Packages

- `etc/ffmpeg/` — ffmpeg utilities and duration string parsing
- `etc/jdk/` — JDK utilities (logging, path manipulation, duration formatting)
- `etc/circe/` — JSON codecs
- `etc/zio/` — ZIO effect utilities
- `etc/methanol/` — HTTP client logging interceptor
- `util/` — General utilities (hex, list, number, vector)

## Configuration

Config is in `src/main/resources/reference.conf`. All values can be overridden via env vars:

| Env Var | Default | Description |
|---|---|---|
| `BEHOLDER_API_KEY` | `foo` | API authentication key |
| `BEHOLDER_HTTP_PORT` | `8080` | HTTP server port |
| `BEHOLDER_CACHE_SIZE` | `500` | Max cache size in MB |
| `BEHOLDER_CACHE_FREEPCT` | `0.20` | Fraction to free when cache is full |
| `BEHOLDER_CAPTURE_THREADS` | `0` (auto) | Max concurrent ffmpeg processes. Auto = `max(2, cores/4)` |
| `BEHOLDER_CAPTURE_QUEUESIZE` | `0` (auto) | Requests that may wait before 503. Auto = `threads * 8` |
| `BEHOLDER_CAPTURE_MAXWAIT` | `15s` | A queued request that waited this long is dropped, not run. `0` disables |
| `BEHOLDER_FFMPEG_TIMEOUT` | `60s` | A capture running longer than this is killed |
| `BEHOLDER_FFPROBE_TIMEOUT` | `30s` | A probe running longer than this is killed |
| `BEHOLDER_FFMPEG_PATH` | `ffmpeg` | Path to the ffmpeg executable |
| `BEHOLDER_FFPROBE_PATH` | `ffprobe` | Path to the ffprobe executable |
| `BEHOLDER_FFPROBE_CACHE_MAXCOUNT` | `20000` | Max probe results cached |
| `BEHOLDER_FFPROBE_CACHE_EXPIRE` | `12h` | Probe cache entry expiry |

## Libraries

- **[Tapir](https://tapir.softwaremill.com/)** + **[Vert.x](https://vertx.io/)** — HTTP server and endpoint definitions
- **[circe](https://circe.github.io/circe/)** — JSON encoding/decoding
- **[ZIO](https://zio.dev/)** — Effect system
- **[munit](https://scalameta.org/munit/)** — Test framework
- **[picocli](https://picocli.info/)** — CLI argument parsing
- **[Methanol](https://github.com/mizosoft/methanol)** — HTTP client (for video URL fetching)
- **slf4j + logback** — Logging (use `java.lang.System.Logger` in source code)

## Docker

The image is based on `eclipse-temurin:25-jdk-resolute` and installs `ffmpeg` (which
supplies `ffprobe` too) via `apt-get`. The entrypoint is `/opt/docker/bin/beholder
/opt/beholder/cache` with port 8080 exposed and `/opt/beholder/cache` as a volume.

**ffmpeg version matters for cropping, so the build pins and checks it.** Container-level clean-aperture (clap) cropping only landed around ffmpeg 7.1, and `-apply_cropping` is a single boolean — there is no `codec`/`container` split — which is why `FfprobeUtil` exists.

Two guards in `build.sbt` keep that from drifting:

- The base image is pinned to the **Ubuntu release** (26.04 "resolute"), not a floating tag and not a digest. The distro determines ffmpeg's major series (8.0.x here); a release tag still picks up patched rebuilds, where a digest would freeze JDK security updates too.
- The apt step asserts `ffmpeg` **and** `ffprobe` exist and that ffmpeg's major version is at least `minFfmpegMajorVersion` (7). A build on an older ffmpeg fails loudly instead of shipping a broken crop path. Verified: the same step on `eclipse-temurin:25-jdk-noble` (Ubuntu 24.04, ffmpeg 6.1.1) exits 1.

Moving to a newer Ubuntu or JDK is therefore a deliberate bump — re-check ffmpeg when you make one.

History worth knowing: the image previously ran ffmpeg 6.1.1 on Ubuntu 24.04, which ignores clap atoms entirely, while a Homebrew ffmpeg 8.x honors them. A clap bug could reproduce locally and not in the container. Both are now on 8.x, so that divergence is gone.

Note the codec-crop bug is *not* version-specific and reproduces on ffmpeg 8.0.1 in the container: a 1920x1080 H.264 file captured with `-apply_cropping 0` and no crop filter comes out **1920x1088**; adding `-vf crop=min(iw\,1920):min(ih\,1080):0:0` restores **1920x1080**. (The clap half of the behavior has not been re-verified in-container against a purpose-built clap MOV; alignment with Homebrew is inferred from the matching version.)

## Code Style

- Scala 3 with indent-based syntax (no braces). Run `sbt compile` then `sbt scalafmtAll` to convert.
- Apache 2.0 license headers are auto-managed by `sbt-header` (`AutomateHeaderPlugin`).
- Versioning is derived from git tags using `sbt-git` (semver format without leading `v`).
