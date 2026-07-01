package com.tourcrm.config;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.common.BusinessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException error) {
        return ApiResponse.fail(error.getMessage());
    }
}

