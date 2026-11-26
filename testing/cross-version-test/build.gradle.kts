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
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.Duration

/*
 * The last release beside this tree (CONTRIBUTING.md, "Releases and versioning"): servers of both versions on one Redis,
 * the release's client and starter against this server, and this client against the release's server. The CI job
 * Cross-Version Compatibility runs it, `./gradlew :cross-version-test:crossVersionTest`; `check` leaves it out.
 *
 * Both clients have the same packages, so each suite has a classpath of its own: `releaseTest` holds only the
 * release's artifacts, with the versions it was released with, and runs this tree's server as a process of its own;
 * `currentTest` holds this tree's client. The release's server is its image. What starts the servers and Redis is in
 * src/main, which each suite runs on its own versions.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

/**
 * The release, moved forward once each one is published. `-PbaselineVersion=` tries another.
 */
val baseline: String = providers.gradleProperty("baselineVersion").getOrElse("8.0.0-rc.2")

repositories {
    // From Maven Central, as an application gets it: never from a mavenLocal() that holds this tree, whose version may
    // still be the release's.
    exclusiveContent {
        forRepository { mavenCentral() }
        filter { includeGroup("me.ahoo.cosky") }
    }
}

kotlin {
    jvmToolchain(21)
}

// The suites' folders, which detekt does not look in by itself, and what it leaves out of tests left out of them.
configure<DetektExtension> {
    source.setFrom("src/main/kotlin", "src/releaseTest/kotlin", "src/currentTest/kotlin")
    config.from(file("detekt.yml"))
}

evaluationDependsOn(":server")
val serverInstall = project(":server").tasks.named<Sync>("installDist")

// The server runs on JDK 25, as in its image (design D27).
val serverJava = the<JavaToolchainService>().launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    // Each suite brings these, in its own versions.
    compileOnly(platform(project(":dependencies")))
    compileOnly("org.testcontainers:testcontainers")
}

/**
 * What both suites share: JUnit, and a test task that keeps what the servers log.
 */
fun JvmTestSuite.crossVersion() {
    useJUnitJupiter()
    targets.all {
        testTask.configure {
            systemProperty("cosky.baseline.version", baseline)
            systemProperty("cosky.logs", layout.buildDirectory.dir("cross-version").get().asFile.path)
            testLogging {
                exceptionFormat = TestExceptionFormat.FULL
            }
            // A hang fails, naming the test and printing every thread's stack, rather than holding CI.
            systemProperty("junit.jupiter.execution.timeout.default", "5m")
            systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
            timeout.set(Duration.ofMinutes(20))
        }
    }
}

testing {
    suites {
        register<JvmTestSuite>("releaseTest") {
            crossVersion()
            dependencies {
                implementation(project())
                implementation(platform("me.ahoo.cosky:cosky-dependencies:$baseline"))
                implementation("me.ahoo.cosky:cosky-client:$baseline")
                implementation("me.ahoo.cosky:cosky-conformance-test:$baseline")
                implementation("me.ahoo.cosky:cosky-spring-boot-starter:$baseline")
                implementation("org.springframework.boot:spring-boot-starter-webflux")
                implementation("org.springframework.cloud:spring-cloud-context")
                implementation("com.google.protobuf:protobuf-java-util")
                implementation("org.testcontainers:testcontainers")
                implementation("me.ahoo.test:fluent-assert-core")
            }
            targets.all {
                testTask.configure {
                    inputs.files(serverInstall).withPropertyName("currentServer")
                    systemProperty("cosky.current.home", serverInstall.get().destinationDir.path)
                    doFirst {
                        systemProperty(
                            "cosky.current.java-home",
                            serverJava.get().metadata.installationPath.asFile.path,
                        )
                    }
                }
            }
        }
        register<JvmTestSuite>("currentTest") {
            crossVersion()
            dependencies {
                implementation(project())
                implementation(platform(project(":dependencies")))
                implementation(platform(libs.junit.bom))
                implementation(project(":client"))
                implementation("org.testcontainers:testcontainers")
                implementation("me.ahoo.test:fluent-assert-core")
            }
        }
    }
}

val crossVersionTest by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the release $baseline beside this tree."
    dependsOn(tasks.named("releaseTest"), tasks.named("currentTest"))
}
