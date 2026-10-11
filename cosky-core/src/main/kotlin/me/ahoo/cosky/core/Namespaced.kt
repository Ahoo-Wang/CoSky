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
package me.ahoo.cosky.core

/**
 * Namespaced.
 *
 * @author ahoo wang
 */
interface Namespaced {
    val namespace: String

    companion object {
        const val DEFAULT: String = "${CoSky.COSKY}-{default}"
        const val SYSTEM: String = "${CoSky.COSKY}-{system}"
    }
}

/**
 * Validates [namespace]: it must not be blank and must not contain [CoSky.KEY_SEPARATOR], which delimits the
 * namespace in every Redis key.
 *
 * @return [namespace]
 */
fun requireValidNamespace(namespace: String): String {
    require(namespace.isNotBlank()) { "namespace must not be blank!" }
    require(!namespace.contains(CoSky.KEY_SEPARATOR)) {
        "namespace:[$namespace] must not contain [${CoSky.KEY_SEPARATOR}]!"
    }
    return namespace
}
