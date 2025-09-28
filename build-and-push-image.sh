#! /bin/bash

./gradlew clean wiremock-grpc-extension-standalone:shadowJar

## extract version from jar

docker build -t wiremock-grpc:0.11.0 .