package com.zoufx.ai.agent.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常 → HTTP 响应翻译。
 *
 * v0 之前的"参数缺失返回 200 + SSE error 事件"语义错位（SSE error 是流中途出错），
 * 改造后参数校验失败统一走 HTTP 400 + JSON，前端按 HTTP 错误分支处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> onValidation(WebExchangeBindException ex) {
        String message = ex.getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        // controller 用 produces=TEXT_EVENT_STREAM_VALUE 会让 Spring 把异常响应也包成 SSE，
        // 显式指定 JSON 让 4xx 响应保持标准 REST 语义
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "VALIDATION_FAILED",
                        "message", message,
                        "timestamp", Instant.now().toString()
                ));
    }
}
