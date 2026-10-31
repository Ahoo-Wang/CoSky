# Built from the distribution: ./gradlew :server:installDist && docker build -t cosky-server server
#
# Try it without Redis or sign-in:
#   docker run -p 8080:8080 -p 9090:9090 cosky-server --dev
#
# For real (design 6.3, 7.6):
#   docker run -p 8080:8080 -p 9090:9090 \
#     -e COSKY_STORE_REDIS_URI=redis://redis:6379 -e COSKY_BOOTSTRAP_TOKEN=cosky_<id>_<secret> cosky-server

ARG APP_NAME=cosky-server
ARG WORK_HOME=/opt/${APP_NAME}

FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:25-ubuntu AS base

FROM base AS build
ARG WORK_HOME
ARG APP_NAME

WORKDIR ${WORK_HOME}
COPY build/install/${APP_NAME} .

FROM base AS run
ARG WORK_HOME
ARG APP_NAME

LABEL maintainer="ahoowang@qq.com"

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=build --chown=appuser:appgroup ${WORK_HOME} ${WORK_HOME}

WORKDIR ${WORK_HOME}
# 8080: REST, Dashboard and Actuator. 9090: gRPC.
EXPOSE 8080 9090
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# Compact object headers save about 9% of memory on the Watch Cache (design 7.10).
ENV JAVA_OPTS="-XX:+UseCompactObjectHeaders -XX:MaxRAMPercentage=75"

USER appuser
STOPSIGNAL SIGTERM

ENTRYPOINT ["bin/cosky-server"]
