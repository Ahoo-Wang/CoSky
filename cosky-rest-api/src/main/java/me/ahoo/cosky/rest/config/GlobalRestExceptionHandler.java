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

package me.ahoo.cosky.rest.config;

import me.ahoo.cosky.core.CoskyException;
import me.ahoo.cosky.rest.dto.ErrorResponse;
import me.ahoo.cosky.rest.security.CoSkySecurityException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global Rest Exception Handler.
 *
 * @author ahoo wang
 */
@Component
@RestControllerAdvice
@Slf4j
public class GlobalRestExceptionHandler {
    
    @ExceptionHandler(CoSkySecurityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleCoSkySecurityException(CoSkySecurityException ex) {
        if (log.isInfoEnabled()) {
            log.info(ex.getMessage(), ex);
        }
        return ErrorResponse.of(ex.getMessage());
    }
    
    @ExceptionHandler(CoskyException.class)
    @ResponseStatus
    public ErrorResponse handleCoSkyException(CoskyException ex) {
        if (log.isWarnEnabled()) {
            log.warn(ex.getMessage(), ex);
        }
        return ErrorResponse.of(ex.getMessage());
    }
    
    @ExceptionHandler
    @ResponseStatus
    public ErrorResponse handleAll(Exception ex) {
        if (log.isErrorEnabled()) {
            log.error(ex.getMessage(), ex);
        }
        return ErrorResponse.of(ex.getMessage());
    }
}
