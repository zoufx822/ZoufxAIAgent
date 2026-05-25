package com.zoufx.ai.agent.soul.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOUL 配置（prefix=ai.soul）。
 *
 * <p>seed 默认值在 {@code SqliteSoulStore.DEFAULT_SEED}，词表在 {@code MoodPromptSection} 常量。
 * 本类仅承载 yml 可覆盖的运行参数。
 */
@Data
@ConfigurationProperties(prefix = "ai.soul")
public class SoulProperties {

    private Mood mood = new Mood();

    @Data
    public static class Mood {
        /** 总开关。false 时不注入 mood 指令、不剥离、不发 SSE 事件。 */
        private boolean enabled = true;
        /** content 流尾部扫描窗口长度（字符），需 ≥ mood 注释总长。 */
        private int tailBufferSize = 32;
    }
}
