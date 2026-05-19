package com.zoufx.ai.agent.memory.controller;

import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.api.MemoryStream;
import com.zoufx.ai.agent.memory.model.StreamEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 记忆暴露 API（v1.1）—— 给前端 StatePanel / Heartbeat 拉对方画像 + 经历流的只读入口。
 *
 * <p>==无鉴权==——同 v0~v1 风格，单机开发环境。任何 userId 都能查任何 userId 的数据；
 * v3 / 真上线前必须补。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>{@code hot} 直接返回 {@code Map<String,String>}——前端按需筛白名单字段</li>
 *   <li>{@code stream} 走 {@link MemoryStream#loadRecent}，limit 上限 50 防止误用拉全表</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/user/{userId}/memory")
@RequiredArgsConstructor
public class MemoryController {

    /** 单次 stream 拉取的硬上限，超过即裁剪到此值。 */
    private static final int STREAM_LIMIT_MAX = 50;

    private final HotMemoryStore hotMemoryStore;
    private final MemoryStream memoryStream;

    /** Hot Memory snapshot：返回该 userId 写入过的全部 key/value（不做白名单过滤，前端按需用）。 */
    @GetMapping("/hot")
    public Mono<Map<String, String>> hot(@PathVariable String userId) {
        return Mono.fromCallable(() -> hotMemoryStore.snapshot(userId));
    }

    /**
     * 最近 N 条经历流。按 created_at DESC 返回，limit 默认 5、上限 50。
     */
    @GetMapping("/stream")
    public Mono<List<StreamEntry>> stream(@PathVariable String userId,
                                          @RequestParam(defaultValue = "5") int limit) {
        int effective = Math.max(1, Math.min(limit, STREAM_LIMIT_MAX));
        return memoryStream.loadRecent(userId, effective);
    }
}
