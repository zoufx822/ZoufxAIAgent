package com.zoufx.ai.agent.chat.support;

import com.zoufx.ai.agent.tool.api.ToolPrompt;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时收集所有 {@link ToolPrompt} 的 methodSections() 声明，
 * 建立 Java 方法名 → 工具中文显示名的映射。
 * {@code @PostConstruct} 保证在 Web Server 启动前就绪，首个 HTTP 请求到达时 map 已可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNameMap {

    private final List<ToolPrompt> tools;
    private final Map<String, String> javaMethodToSection = new HashMap<>();

    @PostConstruct
    public void init() {
        for (ToolPrompt tool : tools) {
            for (var entry : tool.methodSections().entrySet()) {
                javaMethodToSection.put(entry.getKey(), entry.getValue());
            }
        }
        log.info("ToolNameMap initialized with {} entries", javaMethodToSection.size());
    }

    /** Java 方法名 → 工具中文显示名（如 search_web → "联网搜索"），未找到返回方法名本身。 */
    public String toolName(String javaMethodName) {
        return javaMethodToSection.getOrDefault(javaMethodName, javaMethodName);
    }
}
