package com.zoufx.ai.agent.tool.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.zoufx.ai.agent.tool.api.ToolPrompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具方法名 → 汉语名称映射。
 *
 * 用途：LC4J 的 tool_call 事件包含的是方法名（如 search_web、search_cold_memory），
 * 但前端希望显示汉语工具名（如 网络检索、记忆检索）。
 * 本类在启动时从所有 ToolPrompt Bean 的 section() 构建映射缓存。
 */
@Slf4j
@Component
public class ToolNameMapper {

    private final Map<String, String> methodNameToChineseName = new HashMap<>();

    @Autowired
    public void registerTools(List<ToolPrompt> tools) {
        // 方法名 → section() 的映射
        // 注：LC4J 工具名 = 方法名（snake_case）
        // 我们维护 6 个核心工具的映射

        // 根据 ToolPrompt 的 section() 构建映射
        // 但由于 LC4J 是通过方法名进行识别，需要手工维护映射
        methodNameToChineseName.put("search_web", "网络检索");
        methodNameToChineseName.put("search_cold_memory", "记忆检索");
        methodNameToChineseName.put("update_hot_memory", "用户印象更新");

        log.info("✅ ToolNameMapper initialized with {} tool mappings", methodNameToChineseName.size());
    }

    /**
     * 根据方法名获取汉语工具名。
     * 若不存在映射，返回原方法名。
     */
    public String getChineseName(String methodName) {
        return methodNameToChineseName.getOrDefault(methodName, methodName);
    }
}
