package com.zoufx.ai.agent.tool.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网络搜索高层配置（v0.13 起 prefix 从 ai.web-search 迁到 ai.tools.web-search）。
 *
 * <p>通过 type 字段标记当前使用哪个搜索引擎实现（tavily / google / bing 等），
 * 为多引擎可插拔留口子。
 *
 * <p>v0.13 命名空间重整：所有工具配置统一归到 {@code ai.tools.*} 子树下，与
 * {@code ai.llm / ai.memory / ai.soul} 平级。当前只有 web-search 一个工具有配置
 * （hot_memory 工具等是纯 LLM 工具，无 yml 配置），但 ai.tools 命名空间预占住，
 * 未来工具增多时无需再次重命名。
 */
@Data
@ConfigurationProperties(prefix = "ai.tools.web-search")
public class WebSearchProperties {

    private boolean enabled = true;
    private String type = "tavily";
}
