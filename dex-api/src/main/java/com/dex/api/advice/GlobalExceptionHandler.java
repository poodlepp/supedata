package com.dex.api.advice;

import com.dex.common.exception.BusinessException;
import com.dex.common.model.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e) {
        return new ApiResponse<>(500, e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        return ApiResponse.error(e.getMessage());
    }
}
