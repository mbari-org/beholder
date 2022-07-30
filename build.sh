#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "--- Building beholder (reminder: run docker login first!!)"

VCS_REF=`git tag | sort -V | tail -1`

sbt docker:stage \
    && cd $SCRIPT_DIR/target/docker/stage \
    && docker buildx build --platform linux/amd64,linux/arm64 \
        -t mbari/beholder:${VCS_REF} \
        -t mbari/beholder:latest \
        --push . \
    && docker pull mbari/beholder:latest