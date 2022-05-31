# docker run --name cosky-rest-api -d -p 8080:8080 --link redis -e SPRING_REDIS_URL=redis://redis:6379 ahoowang/cosky-rest-api:2.0.0

ARG APP_NAME=cosky-rest-api
ARG WORK_HOME=/opt/${APP_NAME}

FROM openjdk:17-jdk-slim AS base

FROM base as build
ARG WORK_HOME
ARG APP_NAME

WORKDIR ${WORK_HOME}
COPY build/install/${APP_NAME} .

FROM base as run
ARG WORK_HOME

LABEL maintainer="ahoowang@qq.com"
COPY --from=build ${WORK_HOME} ${WORK_HOME}

WORKDIR ${WORK_HOME}
EXPOSE 8080

ENTRYPOINT ["bin/cosky-rest-api"]
