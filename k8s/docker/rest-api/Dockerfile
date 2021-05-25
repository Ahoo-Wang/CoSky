# docker buildx build --push --platform linux/arm/v7 --build-arg GOVERN_VERSION=0.9.20 --build-arg JDK_VERSION=armv7l-centos-jdk-11.0.11_9-slim -t ahoowang/govern-service:0.9.20-armv7 .
# docker buildx build --push --platform linux/amd64,linux/arm64 --build-arg GOVERN_VERSION=0.9.20 --build-arg JDK_VERSION=jdk11u-centos-nightly-slim -t ahoowang/govern-service:0.9.20 .

ARG JDK_VERSION=jdk11u-centos-nightly
ARG GOVERN_VERSION=0.9.20
ARG GOVERN_SERVICE_HOME=/govern-service
FROM adoptopenjdk/openjdk11:${JDK_VERSION} AS base

ARG GOVERN_VERSION
RUN echo "Building Govern-Service ${GOVERN_VERSION}"

FROM curlimages/curl as build
ARG GOVERN_VERSION
ARG GOVERN_SERVICE_HOME
USER root

WORKDIR ${GOVERN_SERVICE_HOME}

ENV DOWN_URL=https://github.com/Ahoo-Wang/govern-service/releases/download
ENV GOVERN_SERVICE_REST_API_TAR=govern-rest-api-${GOVERN_VERSION}.tar
ENV GOVERN_SERVICE_REST_API_TAR_URI=${DOWN_URL}/${GOVERN_VERSION}/${GOVERN_SERVICE_REST_API_TAR}

RUN echo "Downloading : ${GOVERN_SERVICE_REST_API_TAR_URI}"
RUN curl -L ${GOVERN_SERVICE_REST_API_TAR_URI} -o ${GOVERN_SERVICE_REST_API_TAR} ;\
        tar -xvf ${GOVERN_SERVICE_REST_API_TAR};\
        rm ${GOVERN_SERVICE_REST_API_TAR}

FROM base as run
ARG GOVERN_VERSION
ARG GOVERN_SERVICE_HOME

LABEL maintainer="ahoowang@qq.com"
COPY --from=build ${GOVERN_SERVICE_HOME} ${GOVERN_SERVICE_HOME}

WORKDIR ${GOVERN_SERVICE_HOME}/govern-rest-api-${GOVERN_VERSION}
EXPOSE 8080

ENTRYPOINT ["bin/govern-rest-api"]
