# Development

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

Beholder requires that [ffmpeg](https://ffmpeg.org/) 7.1 or newer, including `ffprobe`, is installed to function correctly. Both ship in the same package on Homebrew and Debian/Ubuntu. Without `ffprobe` captures still succeed, but H.264/HEVC frames keep their codec padding rows.

The docker build includes ffmpeg: the base image is pinned to an Ubuntu release so the ffmpeg major version cannot drift, and the build fails if ffmpeg is older than the required major or if either binary is missing. See the Docker section of `CLAUDE.md`.

## Useful Commands

1. `stage` - Build runnable project in `target/universal`
2. `universal:packageBin` - Build zip files of runnable project in `target/universal`
3. `laikaSite` - Build documentation, including API docs to `target/docs/site`
4. `compile` then `scalafmtAll` - Will convert all syntax to new-style, indent based Scala 3.

## Libraries

- [circe](https://circe.github.io/circe/) for JSON handling
- [Methanol](https://github.com/mizosoft/methanol) with [Java's HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html) for HTTP client
- [munit](https://github.com/scalameta/munit) for testing
- [picocli](https://picocli.info/) for command line arg parsing
- [slf4j](http://www.slf4j.org/) with [logback](http://logback.qos.ch/) for logging. Use java.lang.System.Logger
- [ZIO](https://zio.dev/) for effects
