package com.zoufx.ai.agent.soul.config;

import com.zoufx.ai.agent.soul.SqliteSoulStore;
import com.zoufx.ai.agent.soul.property.SoulProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SOUL 灵魂系统装配（v1.1）。
 *
 * 职责：
 * - SqliteSoulStore Bean 注册
 * - SOUL 初始化和种子注入
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SoulConfig {

    private final SoulProperties properties;

    @Bean
    public SqliteSoulStore soulStore(SqliteSoulStore impl) {
        log.info("SOUL store initialized [enabledKeys={}]", properties.getEnabledKeys().size());
        return impl;
    }
}
