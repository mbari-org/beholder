#!/usr/bin/env bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo "--- Building beholder (reminder: run docker login first!!)"

ARCH=$(uname -m)
if [[ $ARCH == 'arm64' ]]; then
    sbt 'Docker / stage'
    cd $SCRIPT_DIR/target/docker/stage
    VCS_REF=`git tag | sort -V | tail -1`
    # https://betterprogramming.pub/how-to-actually-deploy-docker-images-built-on-a-m1-macs-with-apple-silicon-a35e39318e97
    docker buildx build \
      --platform linux/amd64,linux/arm64 \
      -t mbari/beholder:${VCS_REF} \
      -t mbari/beholder:latest \
      --push . \
    && docker pull mbari/beholder:latest
else
    sbt 'Docker / publish'
fi