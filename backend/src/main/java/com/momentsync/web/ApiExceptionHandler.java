package com.momentsync.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> business(ApiException error) {
        return ResponseEntity.status(error.status()).body(ApiResponse.error(error.code(), error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> invalid() {
        return business(ApiException.invalid("请求参数格式不正确"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> tooLarge() {
        return business(new ApiException(413, 41301, "单张图片不能超过 10MB"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ResponseEntity<ApiResponse<Void>> invalidMultipart() {
        return business(ApiException.invalid("上传表单格式不正确"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> unsupportedType() {
        return business(new ApiException(415, 41501, "请求 Content-Type 不支持"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error) {
        LOG.error("业务接口请求失败", error);
        return ResponseEntity.status(500).body(ApiResponse.error(50001, "服务暂时不可用，请稍后重试"));
    }
}
