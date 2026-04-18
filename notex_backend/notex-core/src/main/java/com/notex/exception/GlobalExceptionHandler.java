package com.notex.exception;

import com.notex.domain.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 全局异常处理器
 * 统一处理应用中抛出的异常，返回标准的Response格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常
     *
     * @param e 参数校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Response<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldError()
                .getDefaultMessage();
        log.warn("参数校验失败: {}", message);
        return Response.error(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return e.toResponse();
    }

    @ExceptionHandler(NonTransientAiException.class)
    public Response<String> handleNonTransientAiException(NonTransientAiException e) {
        return Response.error(HttpStatus.INTERNAL_SERVER_ERROR, "API Service Error", e.getMessage());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public Response<String> handleWebClientResponseExceptionException(WebClientResponseException e) {
        return Response.error(HttpStatus.INTERNAL_SERVER_ERROR, "API Service Error", e.getMessage());
    }

    /**
     * 处理其他异常
     *
     * @param e 异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.error("系统异常，请稍后重试");
    }
}
