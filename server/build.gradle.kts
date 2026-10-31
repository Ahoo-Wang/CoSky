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
}

dependencies {
    implementation(project(":store-redis"))
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.google.protobuf:protobuf-java-util")
    implementation("com.google.code.gson:gson")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-test")
    testImplementation(project(":test"))
    testImplementation("io.grpc:grpc-inprocess")
}

// The server runs on JDK 25 and compiles to Java 21 like every other module (design D27).
val javaToolchains = the<JavaToolchainService>()

tasks.withType<Test> {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    // ServerMeasurement reports numbers rather than asserting them, so it only runs when asked for.
    environment("COSKY_MEASURE", System.getenv("COSKY_MEASURE").orEmpty())
    System.getenv("COSKY_MEASURE_JVM_ARGS")?.let { jvmArgs(it.split(" ")) }
    maxHeapSize = System.getenv("COSKY_MEASURE_HEAP") ?: "512m"
}
