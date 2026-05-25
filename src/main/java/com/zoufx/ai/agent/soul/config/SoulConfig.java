package com.zoufx.ai.agent.soul.config;

import com.zoufx.ai.agent.soul.impl.SqliteSoulStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SOUL 系统装配——注册 SqliteSoulStore Bean。
 */
@Slf4j
@Configuration
public class SoulConfig {

    @Bean
    public SqliteSoulStore soulStore(SqliteSoulStore impl) {
        log.info("SOUL store initialized");
        return impl;
    }
}
