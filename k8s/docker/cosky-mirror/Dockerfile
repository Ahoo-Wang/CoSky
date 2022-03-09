# docker buildx build --push --platform linux/arm/v7 --build-arg COSKY_VERSION=1.3.20 --build-arg JDK_VERSION=armv7l-centos-jdk-11.0.11_9-slim -t ahoowang/cosky-mirror:1.3.20-armv7 .
# docker buildx build --push --platform linux/amd64,linux/arm64 --build-arg COSKY_VERSION=1.3.20 --build-arg JDK_VERSION=jdk11u-centos-nightly-slim -t ahoowang/cosky-mirror:1.3.20 .

ARG JDK_VERSION=jdk11u-centos-nightly-slim
ARG COSKY_VERSION=1.3.20
ARG COSKY_HOME=/cosky
FROM adoptopenjdk/openjdk11:${JDK_VERSION} AS base

ARG COSKY_VERSION
RUN echo "Building CoSky-Mirror ${COSKY_VERSION}"

FROM curlimages/curl as build
ARG COSKY_VERSION
ARG COSKY_HOME
USER root

WORKDIR ${COSKY_HOME}

ENV COSKY_MIRROR_TAR=cosky-mirror-${COSKY_VERSION}.tar
COPY ${COSKY_MIRROR_TAR} .
RUN tar -xvf ${COSKY_MIRROR_TAR};\
        rm ${COSKY_MIRROR_TAR}

FROM base as run
ARG COSKY_VERSION
ARG COSKY_HOME

LABEL maintainer="ahoowang@qq.com"
COPY --from=build ${COSKY_HOME} ${COSKY_HOME}

WORKDIR ${COSKY_HOME}/cosky-mirror-${COSKY_VERSION}

ENTRYPOINT ["bin/cosky-mirror"]
