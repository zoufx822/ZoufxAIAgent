package com.zoufx.ai.agent.prompt.impl;

import com.zoufx.ai.agent.prompt.api.Prompt;
import com.zoufx.ai.agent.prompt.support.PromptContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 表达约束段（order=35）——「## 回复框架」「## 输出格式」两段纯静态文案，
 * 统一约束"如何表达"：回复结构、Markdown 输出形态。
 *
 * <p>情绪不在此处产生：用户情绪由独立分类器识别，页面表情由 presence 事件驱动，
 * 正文不再携带任何情绪标记。
 */
@Component
public class ExpressionPromptImpl implements Prompt {

    /** 四段提示：承接 → 回应 → 关联 → 推进，是参考而非模板，短问短答可整段跳过。 */
    private static final String REPLY_STYLE = """
            ## 回复框架

            回复可以参考以下结构，但它是参考不是模板——短问短答、确认类回复直接跳过，别硬凑：

            1. 【承接】先回应对方的核心信息/问题——让对方知道你收到了
            2. 【回应】展开你的回答（观点/信息/建议），此时自然使用工具
            3. 【关联】如果 hot memory 中有相关信息，自然地引用
            4. 【推进】话题自然有延伸时，用开放性结尾往前带一步；没有就利落收住，别为推进硬塞问题，更不要用「还有什么可以帮你的」收尾
            """;

    /**
     * 前端用流式 Markdown 解析器渲染回复，整段被裹进 ``` 围栏会让标题/列表全变字面文本、
     * 内层语言围栏还会错位。此处用命令式 + 反模式堵住模型「把整篇回复当代码展示」的常见毛病。
     */
    private static final String OUTPUT_STYLE = """
            ## 输出格式

            你的回复会被前端按 Markdown 渲染，直接输出 Markdown 正文：

            - 标题用 `#`/`##`、列表用 `-`、强调用 `**`，直接写即可，它们会被渲染成对应样式
            - **绝不**把整段回复包进 ``` 代码围栏。「给我一段 markdown」「用 markdown 格式」的意思是**用 Markdown 排版正文**（直接写标题/列表），**不是**把 Markdown 源码放进代码块展示给对方——哪怕对方这么要求，也直接输出裸 Markdown 正文，最外层绝不套 ``` 或 ```markdown：
              - ❌ 错误：整段回复被 ```markdown … ``` 包住（前端会渲染成一坨灰底代码，标题列表全部失效变成字面文本）
              - ✅ 正确：直接写 `## 今日待办` 换行 `- 第一项`，让它们渲染成真正的标题和列表
            - ``` 代码围栏只用来包裹真正的程序代码片段（js / python / sql 等），且开围栏后必须紧跟语言名
            """;

    @Override
    public int order() {
        return 35;
    }

    @Override
    @Nullable
    public String render(@Nullable PromptContext ctx) {
        return REPLY_STYLE + OUTPUT_STYLE;
    }
}
