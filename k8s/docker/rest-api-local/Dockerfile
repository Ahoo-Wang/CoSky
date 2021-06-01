# docker buildx build --push --platform linux/arm/v7 --build-arg COSKY_VERSION=1.1.0 --build-arg JDK_VERSION=armv7l-centos-jdk-11.0.11_9-slim -t ahoowang/cosky-rest-api:1.1.0-armv7 .
# docker buildx build --push --platform linux/amd64,linux/arm64 --build-arg COSKY_VERSION=1.1.0 --build-arg JDK_VERSION=jdk11u-centos-nightly-slim -t ahoowang/cosky-rest-api:1.1.0 .

ARG JDK_VERSION=jdk11u-centos-nightly-slim
ARG COSKY_VERSION=1.1.0
ARG COSKY_HOME=/cosky
FROM adoptopenjdk/openjdk11:${JDK_VERSION} AS base

ARG COSKY_VERSION
RUN echo "Building CoSky-REST-API ${COSKY_VERSION}"

FROM curlimages/curl as build
ARG COSKY_VERSION
ARG COSKY_HOME
USER root

WORKDIR ${COSKY_HOME}

ENV COSKY_REST_API_TAR=cosky-rest-api-${COSKY_VERSION}.tar
COPY ${COSKY_REST_API_TAR} .
RUN tar -xvf ${COSKY_REST_API_TAR};\
        rm ${COSKY_REST_API_TAR}

FROM base as run
ARG COSKY_VERSION
ARG COSKY_HOME

LABEL maintainer="ahoowang@qq.com"
COPY --from=build ${COSKY_HOME} ${COSKY_HOME}

WORKDIR ${COSKY_HOME}/cosky-rest-api-${COSKY_VERSION}
EXPOSE 8080

ENTRYPOINT ["bin/cosky-rest-api"]
