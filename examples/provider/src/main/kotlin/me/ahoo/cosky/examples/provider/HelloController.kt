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

package me.ahoo.cosky.examples.provider

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * The greeting comes from the Config `provider.yaml`. `@RefreshScope` builds this bean again when it changes.
 */
@RestController
@RefreshScope
class HelloController(@Value("\${example.greeting:Hello}") private val greeting: String) {
    // Plain text whatever the client accepts: as HTML, the name in the path could carry a script.
    @GetMapping("/hello/{name}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun hello(@PathVariable name: String): String = "$greeting, $name"
}
