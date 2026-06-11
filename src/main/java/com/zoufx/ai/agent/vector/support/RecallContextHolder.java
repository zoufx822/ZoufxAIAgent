package com.zoufx.ai.agent.vector.support;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 请求级召回暂存：按 anchorId 存当前请求算好的「## 此刻想起的相关记忆」段。
 *
 * <p>{@code ChatService.prepare} 在召回后 {@link #set}，{@code RecallContextSection.render}
 * 在 compose 时同步 {@link #get}；流终态（complete/error/cancel）{@link #remove}。
 * 同 anchorId double-send 接受"最后写赢"，不做请求级隔离。
 */
@Component
public class RecallContextHolder {

    private final ConcurrentMap<String, String> byAnchor = new ConcurrentHashMap<>();

    public void set(String anchorId, @Nullable String block) {
        if (block == null || block.isBlank()) {
            byAnchor.remove(anchorId);
        } else {
            byAnchor.put(anchorId, block);
        }
    }

    @Nullable
    public String get(String anchorId) {
        return byAnchor.get(anchorId);
    }

    public void remove(String anchorId) {
        byAnchor.remove(anchorId);
    }
}
