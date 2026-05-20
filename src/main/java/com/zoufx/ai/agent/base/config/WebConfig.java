package com.zoufx.ai.agent.base.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://localhost:3001");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 同一份 config 覆盖三个对外暴露的路径前缀：
        //   /ai/**   v0.01 起的聊天 SSE + 清记忆 API
        //   /user/** v0.11 起的 GET 记忆 API（hot snapshot / recent stream）
        //   /admin/** v0.11 起的 SOUL 管理 API
        source.registerCorsConfiguration("/ai/**", config);
        source.registerCorsConfiguration("/user/**", config);
        source.registerCorsConfiguration("/admin/**", config);
        return new CorsWebFilter(source);
    }
}
