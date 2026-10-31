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
}

application {
    applicationName = "cosky-migrate"
    mainClass = "me.ahoo.cosky.migration.MainKt"
    // What the tool has to say goes to the terminal and the report; libraries speak up only when something is wrong.
    applicationDefaultJvmArgs = listOf("-Dorg.slf4j.simpleLogger.defaultLogLevel=error")
}

dependencies {
    implementation(project(":api"))
    implementation("io.lettuce:lettuce-core")
    implementation("com.google.protobuf:protobuf-java-util")
    implementation("com.google.code.gson:gson")
    runtimeOnly("org.slf4j:slf4j-simple")
    testImplementation(project(":server"))
    testImplementation(project(":test"))
    testImplementation("org.springframework.boot:spring-boot")
}

// The server under test brings Logback, and refuses to start beside another provider.
configurations.testRuntimeClasspath {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

// The server under test runs on JDK 25, as it does in production (design D27).
val javaToolchains = the<JavaToolchainService>()

tasks.withType<Test> {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
