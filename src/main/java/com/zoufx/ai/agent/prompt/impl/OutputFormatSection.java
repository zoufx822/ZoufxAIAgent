package com.zoufx.ai.agent.prompt.impl;

import com.zoufx.ai.agent.prompt.api.PromptSection;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 「## 输出格式」段（order=37）——约束回复的 Markdown 输出形态。
 *
 * <p>前端用流式 Markdown 解析器渲染回复，整段被裹进 ``` 围栏会让标题/列表全变字面文本、
 * 内层语言围栏还会错位。此处用命令式 + 反模式堵住模型「把整篇回复当代码展示」的常见毛病。
 */
@Component
public class OutputFormatSection implements PromptSection {

    @Override
    public int order() {
        return 37;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        return "## 输出格式\n\n"
                + "你的回复会被前端按 Markdown 渲染，直接输出 Markdown 正文：\n\n"
                + "- 标题用 `#`/`##`、列表用 `-`、强调用 `**`，直接写即可，它们会被渲染成对应样式\n"
                + "- **绝不**把整段回复包进 ``` 代码围栏——即使对方说「给我一段 markdown」「再来一个」，也直接输出裸 Markdown，不要在最外层套 ```\n"
                + "- ``` 代码围栏只用来包裹真正的程序代码片段（js / python / sql 等），且开围栏后必须紧跟语言名\n";
    }
}
