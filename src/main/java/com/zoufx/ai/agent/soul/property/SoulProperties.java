package com.zoufx.ai.agent.soul.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOUL（AI 自身人格）配置（v1.1）。
 *
 * <p>双重作用：
 * <ul>
 *   <li>{@link #enabledKeys}：SystemPromptComposer 按此顺序注入 SOUL 片段；管理 API 也以此为白名单</li>
 *   <li>{@link #seed}：首启动检测 soul_profile 表为空时批量 INSERT；
 *       ==后续不覆盖已有==——想强制覆盖走 PUT /admin/soul/{key} 或先 DELETE 表</li>
 * </ul>
 *
 * <p>seed 在 yml 用 multi-line 字符串，保留缩进 / 项目符号格式注入 prompt。
 */
@Data
@ConfigurationProperties(prefix = "ai.soul")
public class SoulProperties {

    /** 启用的 SOUL key 顺序（决定 system prompt 注入顺序与管理 API 白名单）。 */
    private List<String> enabledKeys = List.of();

    /** 首启动 seed：当 soul_profile 表为空时批量 INSERT；已有则跳过。 */
    private Map<String, String> seed = new LinkedHashMap<>();
}
