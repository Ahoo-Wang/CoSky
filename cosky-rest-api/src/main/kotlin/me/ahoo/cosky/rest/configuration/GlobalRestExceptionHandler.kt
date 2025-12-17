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
package me.ahoo.cosky.rest.configuration

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.rest.dto.ErrorResponse
import me.ahoo.cosky.rest.dto.ErrorResponse.Companion.of
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Global Rest Exception Handler.
 *
 * @author ahoo wang
 */
@Component
@RestControllerAdvice
class GlobalRestExceptionHandler {
    @ExceptionHandler(SecurityException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleCoSkySecurityException(ex: SecurityException): ErrorResponse {
        log.info(ex) { ex.message.orEmpty() }
        return of(ex.message.orEmpty())
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleAll(ex: Exception): ErrorResponse {
        log.error(ex) { ex.message }
        return of(ex.message.orEmpty())
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
