package com.momentsync.interaction;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

/** 仅作用于本模块 controller；公共错误体系就绪后可用适配器统一错误码。 */
@RestControllerAdvice(assignableTypes = InteractionController.class)
public class InteractionAdvice {
    @ExceptionHandler(InteractionException.class)
    public ResponseEntity<Models.Envelope<Void>> business(InteractionException error) {
        return ResponseEntity.status(error.status()).body(new Models.Envelope<>(error.code(), error.getMessage(), null));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Models.Envelope<Void>> invalid(Exception error) {
        return business(InteractionException.invalid("请求参数格式不正确"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Models.Envelope<Void>> unexpected(Exception error) {
        org.slf4j.LoggerFactory.getLogger(InteractionAdvice.class).error("搜索评论模块请求失败", error);
        return ResponseEntity.status(500).body(new Models.Envelope<>(50001, "服务暂时不可用，请稍后重试", null));
    }
}
