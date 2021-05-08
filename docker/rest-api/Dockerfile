# docker build --build-arg JDK_VERSION=armv7l-centos-jdk-11.0.8_10-slim --build-arg GOVERN_VERSION=0.7.2 -t ahoowang/govern-service:0.7.2 .
ARG JDK_VERSION=alpine
ARG GOVERN_VERSION=0.8.2
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
ENV GOVERN_SERVICE_REST_API_TAR=rest-api-${GOVERN_VERSION}.tar
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

WORKDIR ${GOVERN_SERVICE_HOME}/rest-api-${GOVERN_VERSION}
EXPOSE 8080

ENTRYPOINT ["bin/rest-api"]
