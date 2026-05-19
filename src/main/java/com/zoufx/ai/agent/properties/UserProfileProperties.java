package com.zoufx.ai.agent.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Hot Memory（user_profile 表）启用字段白名单配置（v1.1）。
 *
 * <p>双重作用：
 * <ul>
 *   <li>{@code update_user_profile(key, value)} 工具校验：key 必须在白名单内，
 *       否则拒绝写入——防 LLM 任意写恶意/垃圾字段污染画像</li>
 *   <li>{@code SystemPromptComposer.compose()} 注入「关于对方」段时：
 *       按白名单顺序遍历 snapshot，仅注入有值的 key</li>
 * </ul>
 *
 * <p>v1 只启用 {@code display_name}；v1.1 起扩展到多字段。
 * 后续新字段必须同步在 SystemPromptComposer 加注入模板。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory.hot")
public class UserProfileProperties {

    /** 启用的 key 列表。LLM 调 update_user_profile 时若 key 不在列表内，工具拒绝写入。 */
    private List<String> enabledKeys = List.of("display_name");
}
