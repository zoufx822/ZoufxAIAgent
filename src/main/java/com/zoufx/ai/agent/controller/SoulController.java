package com.zoufx.ai.agent.controller;

import com.zoufx.ai.agent.properties.SoulProperties;
import com.zoufx.ai.agent.memory.SoulStoreContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * SOUL（AI 自身人格）管理 API（v1.1）。
 *
 * <p>==无鉴权==——同 v0~v1 风格，单机开发环境。v3 / 真上线前一定要补鉴权。
 *
 * <p>读 API 也开放给前端（StatePanel 可选展示）。写 API 仅供管理调用——LLM 不能改自己的人格。
 */
@Slf4j
@RestController
@RequestMapping("/admin/soul")
@RequiredArgsConstructor
public class SoulController {

    private final SoulStoreContract soulStore;
    private final SoulProperties properties;

    /** 拉 SOUL 全量 snapshot。 */
    @GetMapping
    public Mono<Map<String, String>> snapshot() {
        return Mono.fromCallable(soulStore::snapshot);
    }

    /**
     * 单 key 写入。
     * key 必须在 enabled-keys 白名单内（防止误写无效字段污染人格表）。
     */
    @PutMapping("/{key}")
    public Mono<ResponseEntity<Map<String, Object>>> set(@PathVariable String key,
                                                         @RequestBody Map<String, String> body) {
        if (!properties.getEnabledKeys().contains(key)) {
            log.warn("⛔ SOUL set rejected: key='{}' not in whitelist", key);
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", "key '" + key + "' not in enabled-keys whitelist"
            )));
        }
        String value = body == null ? null : body.get("value");
        if (value == null || value.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", "body.value cannot be empty"
            )));
        }
        log.info("📝 SOUL set [key={}] value.len={}", key, value.length());
        return soulStore.set(key, value)
                .thenReturn(ResponseEntity.ok(Map.of("key", key, "saved", true)));
    }
}
