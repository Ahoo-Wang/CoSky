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
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.spring)
    kotlin("kapt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.jar.configure {
    exclude("application.yaml", "bootstrap.yaml")
    manifest {
        attributes(
            "Implementation-Title" to application.applicationName,
            "Implementation-Version" to archiveVersion,
        )
    }
}

distributions {
    main {
        contents {
            val dashboardDistPath = "${rootDir.absolutePath}/cosky-dashboard/dist"
            from(dashboardDistPath).include("**")
        }
    }
}

application {
    mainClass.set("me.ahoo.cosky.rest.RestApiServerKt")

    applicationDefaultJvmArgs = listOf(
        "-Xms512M",
        "-Xmx512M",
        "-XX:MaxMetaspaceSize=128M",
        "-XX:MaxDirectMemorySize=256M",
        "-Xss1m",
        "-server",
        "-XX:+UseZGC",
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
        "-Dcosec.root=cosky",
    )
}

dependencies {
    kapt(platform(project(":cosky-dependencies")))
    implementation(platform(project(":cosky-dependencies")))
    implementation("me.ahoo.cosec:cosec-openapi")
    implementation("me.ahoo.cosec:cosec-webflux")
    implementation("me.ahoo.cosec:cosec-spring-boot-starter")
    implementation("io.netty:netty-all")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":cosky-spring-cloud-starter-config"))
    implementation(project(":cosky-spring-cloud-starter-discovery"))
    implementation("com.google.guava:guava")
    implementation("me.ahoo.cosid:cosid-spring-redis")
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.simba:simba-spring-redis")
    implementation("me.ahoo.simba:simba-spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-autoconfigure-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
