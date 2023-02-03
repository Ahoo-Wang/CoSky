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

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:2.7.8"))
    api(platform("org.springframework.cloud:spring-cloud-dependencies:2021.0.5"))
    api(platform("me.ahoo.cosid:cosid-bom:1.16.11"))
    api(platform("me.ahoo.simba:simba-bom:0.6.2"))
    constraints {
        api("com.google.guava:guava:31.1-jre")
        api("commons-io:commons-io:2.11.0")
        api("org.springdoc:springdoc-openapi-kotlin:1.6.14")
        api("org.springdoc:springdoc-openapi-webflux-ui:1.6.14")
        api("org.hamcrest:hamcrest:2.2")
        api("io.mockk:mockk:1.13.4")
        api("org.openjdk.jmh:jmh-core:1.36")
        api("org.openjdk.jmh:jmh-generator-annprocess:1.36")
        api("io.gitlab.arturbosch.detekt:detekt-formatting:1.22.0")
        api("io.jsonwebtoken:jjwt-api:0.11.5")
        api("io.jsonwebtoken:jjwt-impl:0.11.5")
        api("io.jsonwebtoken:jjwt-jackson:0.11.5")
    }
}
