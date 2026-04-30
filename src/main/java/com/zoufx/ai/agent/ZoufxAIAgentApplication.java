package com.zoufx.ai.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.zoufx.ai.agent.config.properties")
public class ZoufxAIAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZoufxAIAgentApplication.class, args);
    }
}
