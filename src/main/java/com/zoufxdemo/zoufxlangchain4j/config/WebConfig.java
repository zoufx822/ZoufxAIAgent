package com.zoufxdemo.zoufxlangchain4j.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源配置 - 开发模式下禁用缓存，确保 JS/CSS 修改立即生效
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/js/**", "/css/**")
                .addResourceLocations("classpath:/static/js/", "classpath:/static/css/")
                .setCacheControl(CacheControl.noStore());
    }
}
