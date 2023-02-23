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

rootProject.name = "CoSky"

include(":cosky-core")
include(":cosky-config")
include(":cosky-discovery")
include(":cosky-bom")
include(":cosky-dependencies")
include(":cosky-spring-cloud-core")
include(":spring-cloud-starter-cosky-config")
include(":spring-cloud-starter-cosky-discovery")
include(":cosky-rest-api")
// TODO
// include(":cosky-mirror")
include(":cosky-test")
include(":code-coverage-report")

include(":cosky-service-provider")
project(":cosky-service-provider").projectDir = file("examples/cosky-service-provider")

include(":cosky-service-provider-api")
project(":cosky-service-provider-api").projectDir = file("examples/cosky-service-provider-api")

include(":cosky-service-consumer")
project(":cosky-service-consumer").projectDir = file("examples/cosky-service-consumer")

pluginManagement {
    plugins {
        id("io.gitlab.arturbosch.detekt") version "1.22.0" apply false
        kotlin("jvm") version "1.8.10" apply false
        kotlin("plugin.spring") version "1.8.10" apply false
        id("org.jetbrains.dokka") version "1.7.20" apply false
        id("me.champeau.jmh") version "0.7.0" apply false
        id("io.github.gradle-nexus.publish-plugin") version "1.2.0" apply false
    }
}

