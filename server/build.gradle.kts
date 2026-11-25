/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.kotlin.spring)
    application
}

application {
    applicationName = "cosky-server"
    mainClass = "me.ahoo.cosky.server.CoSkyServerApplicationKt"
    // In the start script, ahead of JAVA_OPTS and COSKY_SERVER_OPTS, which add to them: setting JAVA_OPTS for a trust
    // store must not drop the heap size. Compact object headers save about 9% of memory on the Watch Cache (design
    // 7.10). Netty loads its epoll transport, which sets the TCP user timeout, through a restricted method that a later
    // JDK refuses unless native access is enabled. Protobuf still reads arrays through sun.misc.Unsafe, which JDK 25
    // warns about at every start. A JVM out of memory is in no state to serve: it exits, and is started again.
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseCompactObjectHeaders",
        "-XX:MaxRAMPercentage=75",
        "-XX:+ExitOnOutOfMemoryError",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
    )
}

dependencies {
    implementation(project(":store-redis"))
    // Lettuce notices a vanished Redis host through epoll. Reactor Netty only brings the x86 library, and the image is
    // built for arm64 too.
    runtimeOnly("io.netty:netty-transport-native-epoll::linux-aarch_64")
    // Spares a server run on a Mac, as in development, an error at every start about the DNS servers it cannot read.
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-x86_64")
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable")
    // Spring needs it to find primary constructors and apply default values; only tests had it by accident.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.google.protobuf:protobuf-java-util")
    implementation("com.google.code.gson:gson")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    // The OpenAPI document generated from the protos, at /v3/api-docs and in Swagger UI.
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-test")
    testImplementation(project(":test"))
    testImplementation(project(":conformance-test"))
    testImplementation(project(":client"))
    testImplementation("io.grpc:grpc-inprocess")
    testImplementation("org.testcontainers:testcontainers-toxiproxy")
}

// The Dashboard, once `pnpm build` has run in dashboard/. A server built without it still works, without a UI (D11).
// And the REST API's OpenAPI document as generated from the protos, which springdoc serves.
tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("dashboard/dist")) {
        into("static")
    }
    dependsOn(":api:generateOpenApi")
    from(project(":api").layout.buildDirectory.file("openapi/cosky.yaml")) {
        into("cosky")
        rename { "openapi.yaml" }
    }
}

// The server runs on JDK 25 and compiles to Java 21 like every other module (design D27).
val javaToolchains = the<JavaToolchainService>()

// The flags above are for JDK 25, which `run` uses as well.
tasks.named<JavaExec>("run") {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<Test> {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    // ConfigurationMetadataTest holds the configuration metadata to the properties reference, in both languages.
    inputs.files(rootProject.files("wiki/guide/properties.md", "wiki/zh/guide/properties.md"))
        .withPropertyName("propertiesReference")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // ServerMeasurement reports numbers rather than asserting them, so it only runs when asked for.
    environment("COSKY_MEASURE", System.getenv("COSKY_MEASURE").orEmpty())
    System.getenv("COSKY_MEASURE_JVM_ARGS")?.let { jvmArgs(it.split(" ")) }
    maxHeapSize = System.getenv("COSKY_MEASURE_HEAP") ?: "512m"
}
