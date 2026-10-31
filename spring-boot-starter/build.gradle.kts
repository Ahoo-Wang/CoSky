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
    api(project(":client"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.cloud:spring-cloud-commons")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    compileOnly("org.springframework.boot:spring-boot-health")
    compileOnly("org.springframework.boot:spring-boot-web-server")
    compileOnly("org.springframework.cloud:spring-cloud-context")
    compileOnly("org.springframework.cloud:spring-cloud-loadbalancer")
    testImplementation(project(":server"))
    testImplementation(project(":store"))
    testImplementation("io.grpc:grpc-netty-shaded")
    testImplementation("org.springframework.boot:spring-boot-health")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.cloud:spring-cloud-context")
    testImplementation("org.springframework.cloud:spring-cloud-loadbalancer")
}
