# beholder

![Build](https://github.com/mbari-org/beholder/actions/workflows/test.yml/badge.svg)

![MBARI logo](src/docs/_assets/images/logo-mbari-3b.png)

<img src="src/docs/_assets/images/beholder.png" alt="beholder" width="250"/>

## Overview

Beholder is a Scala 3 HTTP microservice that extracts individual frames from videos as JPEGs using `ffmpeg`. It exposes a single `POST /capture` endpoint that accepts a video URL and an elapsed time in milliseconds, returns the JPEG image, and caches results on disk to avoid redundant ffmpeg invocations.

## Requirements

- Java 21+
- [ffmpeg](https://ffmpeg.org/download.html) installed and on `PATH`
- [sbt](https://www.scala-sbt.org/) (Scala Build Tool)

## API

### POST /capture

Capture a frame from a video at the given elapsed time. Returns the JPEG image directly, or a cached copy if one exists.

**Headers**

| Header | Required | Description |
|---|---|---|
| `X-Api-Key` | Yes | API key for access (default: `foo`) |
| `Content-Type` | Yes | Must be `application/json` |

**Query Parameters**

| Parameter | Default | Description |
|---|---|---|
| `accurate` | `true` | When `true`, seek to the exact elapsed time. When `false`, seek to the nearest keyframe before it (faster) |
| `nokey` | `false` | When `true`, skip non-key frames after the seek point. Useful for fast processing of some codecs |

**Request Body**

```json
{
  "videoUrl": "http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1436/D1436_20220322T132758Z_h264.mp4",
  "elapsedTimeMillis": 1234
}
```

**Example**

```bash
curl -X POST http://localhost:8080/capture \
  -H "X-Api-Key: foo" \
  -H "Content-Type: application/json" \
  -d '{"videoUrl":"http://example.com/video.mp4","elapsedTimeMillis":1234}' \
  --output frame.jpg
```

**Example with query params**

```bash
curl -X POST "http://localhost:8080/capture?accurate=false&nokey=true" \
  -H "X-Api-Key: foo" \
  -H "Content-Type: application/json" \
  -d '{"videoUrl":"http://example.com/video.mp4","elapsedTimeMillis":5000}' \
  --output frame.jpg
```

### GET /health

Returns HTTP 200 if the service is running.

### Swagger UI

Interactive API documentation is available at `http://localhost:8080/docs` when the server is running.

## Configuration

Settings can be provided via environment variables, a config file, or CLI flags. CLI flags take the highest precedence, followed by environment variables, then the defaults in `reference.conf`.

| Config key | Environment variable | CLI flag | Default |
|---|---|---|---|
| `beholder.api.key` | `BEHOLDER_API_KEY` | `-k` / `--apikey` | `foo` |
| `beholder.http.port` | `BEHOLDER_HTTP_PORT` | `-p` / `--port` | `8080` |
| `beholder.cache.size` | `BEHOLDER_CACHE_SIZE` | `-c` / `--cachesize` | `500` (MB) |
| `beholder.cache.freepct` | `BEHOLDER_CACHE_FREEPCT` | `-f` / `--freepct` | `0.20` |

The cache root directory is a required positional CLI argument (or `BEHOLDER_CACHE_ROOT` env var when running via Docker).

## Development

### Building

```bash
# Compile
sbt compile

# Run all tests (requires ffmpeg)
sbt test

# Run a single test suite
sbt "testOnly org.mbari.beholder.JpegCacheSuite"

# Format code (run before committing)
sbt scalafmtAll

# Generate scaladoc
sbt doc

# Check for dependency updates
sbt dependencyUpdates
```

### Running Locally

```bash
# Start the server with a cache directory
sbt "run /tmp/beholder-cache"

# With custom port and API key
sbt "run --port 9090 --apikey secret /tmp/beholder-cache"
```

### Cache layout

Frames are stored under the cache root as:

```
<root>/<video-url-host>/<video-url-path>/<hh_mm_ss.sss>.jpg
```

For example:

```
/tmp/beholder-cache/m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1436/D1436_20220322T132758Z_h264.mp4/00_00_01.234.jpg
```

The in-memory index is rebuilt from disk on startup, so cached frames survive service restarts.

## Deployment

### Docker

Pre-built multi-architecture images (`linux/amd64`, `linux/arm64`) are published to Docker Hub as [`mbari/beholder`](https://hub.docker.com/r/mbari/beholder).

```bash
docker run -d \
  -p 8080:8080 \
  -e BEHOLDER_API_KEY=your-secret-key \
  -v /data/beholder-cache:/opt/beholder/cache \
  mbari/beholder:latest
```

The container expects a writable volume mounted at `/opt/beholder/cache`. The port defaults to `8080`.

**Environment variables for Docker**

```bash
docker run -d \
  -p 9090:9090 \
  -e BEHOLDER_API_KEY=your-secret-key \
  -e BEHOLDER_HTTP_PORT=9090 \
  -e BEHOLDER_CACHE_SIZE=1000 \
  -e BEHOLDER_CACHE_FREEPCT=0.25 \
  -v /data/beholder-cache:/opt/beholder/cache \
  mbari/beholder:latest
```

### Building a Docker Image

**Standard build (from any platform targeting `linux/amd64`)**

```bash
sbt 'Docker / publish'
```

**Multi-architecture build (required on Apple Silicon)**

```bash
./build.sh
```

This uses `docker buildx` to produce `linux/amd64` and `linux/arm64` images and pushes them to Docker Hub. Run `docker login` first.

### Releasing

Releases are triggered automatically by pushing a git tag that matches the version pattern `[0-9]+.*` (no leading `v`):

```bash
git tag 1.2.3
git push origin 1.2.3
```

The [release workflow](.github/workflows/release.yml) builds and pushes a multi-arch Docker image tagged with both the version number and `latest`.

## Documentation

Full scaladoc is published at <https://mbari-org.github.io/beholder/>

Additional documentation can be added as Markdown files in `src/docs/` and will be included automatically when running `sbt doc`.

---

[Beholder image](src/docs/_assets/images/beholder.png) Copyright © 2022 Aine Schlining
