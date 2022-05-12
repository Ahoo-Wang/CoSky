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
include(":spring-cloud-starter-cosky-discovery-ribbon")
include(":cosky-rest-api")
include(":cosky-mirror")
include(":cosky-test")


include(":cosky-service-provider")
project(":cosky-service-provider").projectDir = file("examples/cosky-service-provider")

include(":cosky-service-provider-api")
project(":cosky-service-provider-api").projectDir = file("examples/cosky-service-provider-api")

include(":cosky-service-consumer")
project(":cosky-service-consumer").projectDir = file("examples/cosky-service-consumer")


buildscript{
    repositories{
        gradlePluginPortal()
    }
    dependencies{
        classpath("me.champeau.jmh:jmh-gradle-plugin:0.6.6")
        classpath("io.github.gradle-nexus:publish-plugin:1.1.0")
        classpath("com.github.spotbugs.snom:spotbugs-gradle-plugin:5.0.6")
    }
}

