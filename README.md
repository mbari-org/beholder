# beholder

![MBARI logo](src/docs/images/logo-mbari-3b.png)

<img src="src/docs/images/beholder.png" alt="beholder" width="250"/>

Beholder extracts individual frames from videos as jpegs. The format of the post request is:

```text
POST http://localhost:8080/capture
X-Api-Key: foo
Accept: image/jpeg

{
  "videoUrl": "http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1436/D1436_20220322T132758Z_h264.mp4",
  "elapsedTimeMillis": 1234
}
```

## Endpoints

- `/docs` - Swagger documentation
- `/capture` - Capture a frame from a video
- `/health` - Health status of the server

## Docker

A docker container can be build and published using `build.sh`

```bash
docker run -d -p 8080:8080 -v /path/to/cache:/opt/beholder/cache --name beholder --restart=always mbari/beholder
```

### Environment variables 

You can pass any of the following environment variables to the beholder container:

- `BEHOLDER_API_KEY` - The API key to use for authentication for the service (i.e. the `X-Api-Key` header)
- `BEHOLDER_CACHE_SIZE` - The size of the cache in megabytes (defaults to 500)
- `BEHOLDER_CACHE_FREEPCT` - The percentage, given as a value between 0 and 1, of cache space to free when the cache is full (defaults to 0.2 (20%))

## Development

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

### Useful Commands

1. `stage` - Build runnable project in `target/universal`
2. `universal:packageBin` - Build zip files of runnable project in `target/universal`
3. `laikaSite` - Build documentation, including API docs to `target/docs/site`
4. `compile` then `scalafmtAll` - Will convert all syntax to new-style, indent based Scala 3.

### Libraries

- [circe](https://circe.github.io/circe/) for JSON handling
- [Methanol](https://github.com/mizosoft/methanol) with [Java's HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html) for HTTP client
- [munit](https://github.com/scalameta/munit) for testing
- [picocli](https://picocli.info/) for command line arg parsing
- [slf4j](http://www.slf4j.org/) with [logback](http://logback.qos.ch/) for logging. Use java.lang.System.Logger
- [ZIO](https://zio.dev/) for effects

### Notes

Documentation can be added as markdown files in `docs` and will be included automatically when you run `laikaSite`.

[Beholder image](docs/images/beholder.png) Copyright © 2022 Aine Schlining
