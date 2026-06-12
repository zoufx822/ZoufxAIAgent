package com.zoufx.ai.agent.tool.config;

import com.zoufx.ai.agent.tool.property.TavilySearchProps;
import com.zoufx.ai.agent.tool.property.WebSearchProps;
import com.zoufx.ai.agent.tool.impl.TavilySearchTool;
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
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ai.tools.web-search", name = "type", havingValue = "tavily", matchIfMissing = true)
public class TavilySearchConfig {

    @Bean
    public TavilySearchTool tavilySearchTool(WebSearchProps webProps, TavilySearchProps tavilyProps) {
        TavilySearchProps.Retry retry = tavilyProps.getRetry();
        if (!webProps.isEnabled()) {
            log.warn("Web search 已禁用（ai.tools.web-search.enabled=false）");
            return new TavilySearchTool(null, tavilyProps.getMaxResults(), retry.getMaxAttempts(), retry.getBackoff().toMillis());
        }

        WebSearchEngine engine = null;
        if (StringUtils.hasText(tavilyProps.getApiKey())) {
            engine = TavilyWebSearchEngine.builder()
                    .apiKey(tavilyProps.getApiKey())
                    .searchDepth(tavilyProps.getSearchDepth())
                    .includeAnswer(tavilyProps.isIncludeAnswer())
                    .timeout(tavilyProps.getTimeout())
                    .build();
            log.info("Tavily 网络检索 engine 已就绪（searchDepth={}, maxResults={}）",
                    tavilyProps.getSearchDepth(), tavilyProps.getMaxResults());
        } else {
            log.warn("TAVILY_API_KEY 未配置，网络检索功能将降级（工具调用返回提示字符串）");
        }
        return new TavilySearchTool(engine, tavilyProps.getMaxResults(), retry.getMaxAttempts(), retry.getBackoff().toMillis());
    }
}
