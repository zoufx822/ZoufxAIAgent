package com.zoufx.ai.agent.tool.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网络搜索高层配置（prefix={@code ai.tools.web-search}）。
 * type 字段标记当前使用哪个搜索引擎实现（tavily / google / bing 等），为多引擎可插拔留口子。
 */
@Data
@ConfigurationProperties(prefix = "ai.tools.web-search")
public class WebSearchProps {

    private boolean enabled = true;
    private String type = "tavily";
}
