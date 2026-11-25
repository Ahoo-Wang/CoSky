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

# Eclipse Temurin's JRE on Ubuntu 24.04 LTS, pinned by digest so that a build is reproducible; Renovate proposes the
# next digest. A JRE: the server needs no compiler, and a smaller image has fewer packages to patch.
FROM eclipse-temurin:25-jre-noble@sha256:f3dd0838e69b881cc382237d5901e74ae3705b1056ad964b2e0e523b174fef99 AS base

FROM base AS build
ARG WORK_HOME
ARG APP_NAME

WORKDIR ${WORK_HOME}
COPY build/install/${APP_NAME} .

FROM base AS run
ARG WORK_HOME
ARG APP_NAME

LABEL maintainer="ahoowang@qq.com"

# curl for the health check below. The user and group are 999, as the Kubernetes manifests run it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r -g 999 appgroup \
    && useradd -r -u 999 -g appgroup -M -s /usr/sbin/nologin appuser

COPY --from=build --chown=appuser:appgroup ${WORK_HOME} ${WORK_HOME}

WORKDIR ${WORK_HOME}
# 8080: REST, Dashboard and Actuator. 9090: gRPC. MANAGEMENT_SERVER_PORT moves Actuator to a port of its own, and the
# health check follows it.
EXPOSE 8080 9090
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f "http://localhost:${MANAGEMENT_SERVER_PORT:-8080}/actuator/health/liveness" || exit 1

# The JVM's flags come with the start script (server/build.gradle.kts), so that JAVA_OPTS adds to them rather than
# replacing them.
USER 999:999
STOPSIGNAL SIGTERM

ENTRYPOINT ["bin/cosky-server"]
