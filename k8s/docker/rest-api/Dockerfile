# docker buildx build --push --platform linux/arm/v7 --build-arg COSKY_VERSION=1.2.0 --build-arg JDK_VERSION=armv7l-centos-jdk-11.2.01_9-slim -t ahoowang/cosky-rest-api:1.2.0-armv7 .
# docker buildx build --push --platform linux/amd64,linux/arm64 --build-arg COSKY_VERSION=1.2.0 --build-arg JDK_VERSION=jdk11u-centos-nightly-slim -t ahoowang/cosky-rest-api:1.2.0 .

ARG JDK_VERSION=jdk11u-centos-nightly-slim
ARG COSKY_VERSION=1.2.0
ARG COSKY_HOME=/cosky
FROM adoptopenjdk/openjdk11:${JDK_VERSION} AS base

ARG COSKY_VERSION
RUN echo "Building CoSky-REST-API ${COSKY_VERSION}"

FROM curlimages/curl as build
ARG COSKY_VERSION
ARG COSKY_HOME
USER root

WORKDIR ${COSKY_HOME}

ENV DOWN_URL=https://github.com/Ahoo-Wang/cosky/releases/download
ENV COSKY_REST_API_TAR=cosky-rest-api-${COSKY_VERSION}.tar
ENV COSKY_REST_API_TAR_URI=${DOWN_URL}/${COSKY_VERSION}/${COSKY_REST_API_TAR}

RUN echo "Downloading : ${COSKY_REST_API_TAR_URI}"
RUN curl -L ${COSKY_REST_API_TAR_URI} -o ${COSKY_REST_API_TAR} ;\
        tar -xvf ${COSKY_REST_API_TAR};\
        rm ${COSKY_REST_API_TAR}

FROM base as run
ARG COSKY_VERSION
ARG COSKY_HOME

LABEL maintainer="ahoowang@qq.com"
COPY --from=build ${COSKY_HOME} ${COSKY_HOME}

WORKDIR ${COSKY_HOME}/cosky-rest-api-${COSKY_VERSION}
EXPOSE 8080

ENTRYPOINT ["bin/cosky-rest-api"]
