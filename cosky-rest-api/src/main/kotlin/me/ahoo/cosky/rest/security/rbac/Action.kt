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
package me.ahoo.cosky.rest.security.rbac

import org.springframework.http.HttpMethod

/**
 * Action.
 *
 * @author ahoo wang
 */
enum class Action(val value: String) {
    READ("r"), WRITE("w"), READ_WRITE("rw");

    fun check(requestAction: Action): Boolean {
        return if (READ_WRITE.value == value) {
            true
        } else this == requestAction
    }

    companion object {
        @JvmStatic
        fun of(value: String): Action {
            return when (value) {
                "r" -> {
                    READ
                }

                "w" -> {
                    WRITE
                }

                "rw" -> {
                    READ_WRITE
                }

                else -> throw IllegalStateException("Unexpected value: $value")
            }
        }

        fun ofHttpMethod(httpMethodStr: String): Action {
            val httpMethod = requireNotNull(HttpMethod.resolve(httpMethodStr))
            return ofHttpMethod(httpMethod)
        }

        @JvmStatic
        fun ofHttpMethod(httpMethod: HttpMethod): Action {
            return when (httpMethod) {
                HttpMethod.GET, HttpMethod.OPTIONS, HttpMethod.TRACE, HttpMethod.HEAD -> READ
                HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH -> WRITE
                else -> throw IllegalStateException("Unexpected value: $httpMethod")
            }
        }
    }
}
