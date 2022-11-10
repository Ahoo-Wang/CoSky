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
package me.ahoo.cosky.rest.dto

/**
 * Error Response.
 *
 * @author ahoo wang
 */
data class ErrorResponse(var code: String = ERROR_CODE, var msg: String) {

    companion object {
        const val ERROR_CODE = "0001"
        fun of(code: String, msg: String): ErrorResponse {
            return ErrorResponse(code, msg)
        }

        @JvmStatic
        fun of(msg: String): ErrorResponse {
            return ErrorResponse(ERROR_CODE, msg)
        }
    }
}
