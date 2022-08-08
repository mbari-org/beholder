# beholder

![Beholder](images/beholder.png)

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

A docker container can be built and published using `build.sh`. Beholder can be used by anyone using docker as follows:

```bash
docker run -d \
  -p 8080:8080\
  -v /path/to/cache:/opt/beholder/cache \
  --name beholder \
  --restart=always \
  mbari/beholder
```

### Environment variables

You can pass any of the following environment variables to the beholder container:

- `BEHOLDER_API_KEY` - The API key to use for authentication for the service (i.e. the `X-Api-Key` header)
- `BEHOLDER_CACHE_SIZE` - The size of the cache in megabytes (defaults to 500)
- `BEHOLDER_CACHE_FREEPCT` - The percentage, given as a value between 0 and 1, of cache space to free when the cache is full (defaults to 0.2 (20%))
