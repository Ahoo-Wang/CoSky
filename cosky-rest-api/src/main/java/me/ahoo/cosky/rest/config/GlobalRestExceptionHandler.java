package me.ahoo.cosky.rest.config;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.rest.dto.ErrorResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author ahoo wang
 */
@Component
@RestControllerAdvice
@Slf4j
public class GlobalRestExceptionHandler {

    @ExceptionHandler
    @ResponseStatus
    public ErrorResponse handleAll(Exception ex) {
        if (log.isErrorEnabled()) {
            log.error(ex.getMessage(), ex);
        }
        return ErrorResponse.of(ex.getMessage());
    }
}
