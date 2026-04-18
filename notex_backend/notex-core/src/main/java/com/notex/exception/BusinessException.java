package com.notex.exception;

import com.notex.domain.Response;
import org.springframework.http.HttpStatus;

/**
 * 业务异常类
 * 用于抛出业务逻辑中的异常，会被全局异常处理器捕获并返回自定义错误信息
 */
public class BusinessException extends RuntimeException {

    private HttpStatus httpStatus;

    /**
     * 构造函数
     *
     * @param message 错误信息
     */
    public BusinessException(String message) {
        super(message);
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    /**
     * 构造函数
     *
     * @param message    错误信息
     * @param httpStatus HTTP状态码
     */
    public BusinessException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * 获取HTTP状态码
     *
     * @return HTTP状态码
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 转换为Response对象
     *
     * @return Response对象
     */
    public Response<Void> toResponse() {
        return Response.error(httpStatus, this.getMessage());
    }
}
