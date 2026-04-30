package com.zoufx.ai.agent.config.ai.tool;

import com.zoufx.ai.agent.config.properties.RetryProperties;
import com.zoufx.ai.agent.config.properties.WebSearchProperties;
import com.zoufx.ai.agent.tool.TavilySearchTool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 网络检索装配。
 * apiKey 为空时仍然注册 TavilySearchTool（engine=null），让工具调用链路完整，
 * 模型真正触发工具时才返回「未配置」字符串降级，避免因缺 key 启动失败。
 *
 * 注意：@ConditionalOnProperty 只控制 langchain4j.web-search.enabled 开关，不依赖 api-key 是否存在。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "langchain4j.web-search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TavilySearchConfig {

    @Bean
    public TavilySearchTool tavilySearchTool(WebSearchProperties props, RetryProperties retryProps) {
        WebSearchProperties.Tavily t = props.getTavily();
        RetryProperties.Tavily r = retryProps.getTavily();
        WebSearchEngine engine = null;
        if (StringUtils.hasText(t.getApiKey())) {
            engine = TavilyWebSearchEngine.builder()
                    .apiKey(t.getApiKey())
                    .searchDepth(t.getSearchDepth())
                    .includeAnswer(t.isIncludeAnswer())
                    .timeout(t.getTimeout())
                    .build();
            log.info("Tavily 网络检索 engine 已就绪（searchDepth={}, maxResults={}）",
                    t.getSearchDepth(), t.getMaxResults());
        } else {
            log.warn("TAVILY_API_KEY 未配置，网络检索功能将降级（工具调用返回提示字符串）");
        }
        return new TavilySearchTool(engine, t.getMaxResults(), r.getMaxAttempts(), r.getBackoff().toMillis());
    }
}
