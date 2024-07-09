# beholder

![Build](https://github.com/mbari-org/beholder/actions/workflows/test.yml/badge.svg)

![MBARI logo](src/docs/images/logo-mbari-3b.png)

<img src="src/docs/images/beholder.png" alt="beholder" width="250"/>

## Overview

Beholder extracts individual frames from videos as jpegs. The format of the post request is:

```text
POST http://localhost:8080/capture
X-Api-Key: foo
Content-Type: application/json

{
  "videoUrl": "http://m3.shore.mbari.org/videos/M3/proxy/DocRicketts/2022/03/1436/D1436_20220322T132758Z_h264.mp4",
  "elapsedTimeMillis": 1234
}
```

## Documentation

Documentation is at <https://mbari-org.github.io/beholder/>

## Notes

Documentation can be added as markdown files in `src/docs` and will be included automatically when you run `laikaSite`.

[Beholder image](docs/images/beholder.png) Copyright © 2022 Aine Schlining
