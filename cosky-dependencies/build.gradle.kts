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
    api(platform(libs.spring.boot.dependencies))
    api(platform(libs.spring.cloud.dependencies))
    api(platform(libs.cosid.bom))
    api(platform(libs.simba.bom))
    api(platform(libs.cosec.bom))
    api(platform(libs.fluent.assert.bom))
    constraints {
        api(libs.guava)
        api(libs.kotlin.logging)
        api(libs.commons.io)
        api(libs.springdoc.openapi.starter.webflux.ui)
        api(libs.mockk)
        api(libs.jmh.core)
        api(libs.jmh.generator.annprocess)
        api(libs.detekt.formatting)
    }
}
