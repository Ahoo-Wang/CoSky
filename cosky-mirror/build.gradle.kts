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
    application
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.10"
    kotlin("kapt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.jar.configure {
    exclude("application.yaml", "bootstrap.yaml")
}

application {
    mainClass.set("me.ahoo.cosky.mirror.CoskyMirrorServerKt")

    applicationDefaultJvmArgs = listOf(
        "-Xms512M",
        "-Xmx512M",
        "-XX:MaxMetaspaceSize=128M",
        "-XX:MaxDirectMemorySize=256M",
        "-Xss1m",
        "-server",
        "-XX:+UseG1GC",
        "-Xlog:gc*:file=logs/$applicationName-gc.log:time,tags:filecount=10,filesize=32M",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=data",
        "-Dcom.sun.management.jmxremote",
        "-Dcom.sun.management.jmxremote.authenticate=false",
        "-Dcom.sun.management.jmxremote.ssl=false",
        "-Dcom.sun.management.jmxremote.port=5555",
        "-Dspring.cloud.bootstrap.enabled=true",
        "-Dspring.cloud.bootstrap.location=config/bootstrap.yaml",
        "-Dspring.config.location=file:./config/",
    )
}

dependencies {
    kapt(platform(project(":cosky-dependencies")))
    implementation(platform(project(":cosky-dependencies")))
    implementation(project(":spring-cloud-starter-cosky-config"))
    implementation(project(":spring-cloud-starter-cosky-discovery"))
    implementation("com.google.guava:guava")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery:2023.0.1.2")
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-autoconfigure-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
