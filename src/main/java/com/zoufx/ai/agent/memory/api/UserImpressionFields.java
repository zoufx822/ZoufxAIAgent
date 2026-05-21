package com.zoufx.ai.agent.memory.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * user-impression type 下启用的字段集 + 每字段的全部元信息（v0.13 硬编码 schema）。
 *
 * <p><b>单一来源</b>——三处消费者共用：
 * <ul>
 *   <li>{@code UserImpressionUpdateTool}：白名单校验读 {@link #FIELDS#keySet()}；
 *       promptInstructions 动态拼接识别规则段 + 白名单字面（{@link #renderDetectionRules()} /
 *       {@link #whitelistLiteral()}）</li>
 *   <li>{@code IdentityPromptSection}：「## 关于对方」段按 key 顺序遍历，按
 *       {@link FieldSpec#renderDirective()} 渲染</li>
 *   <li>{@code @Tool} / {@code @P} 注解描述：仍是字面值（LC4J 编译期常量约束，无法消除）—
 *       手工与本类 keySet() 保持一致</li>
 * </ul>
 *
 * <p><b>v0.13 review 改进：detection rule 内聚到 FieldSpec</b>
 * <ul>
 *   <li>初版（5.1.2 切分）："写后渲染 directive" 放 FIELDS，"何时写 detection rule" 放
 *       {@code UserImpressionUpdateTool.promptInstructions()} 纯文本里——三处同步</li>
 *   <li>改进（本版）：detection 也并入 FIELDS 的 {@link FieldSpec}，
 *       {@code promptInstructions} 用 {@code .formatted()} 拼接动态段——
 *       同步处降到两处（FIELDS / @P 字面），每字段元信息真正"单一来源"</li>
 * </ul>
 *
 * <p><b>为何不放 yml？</b>v0.13 review 后从 yml 改回硬编码：
 * <ul>
 *   <li>字段集是 ==schema 层面==（"这个 Agent 能记关于用户的哪些类型属性"），不是 config——
 *       与 SOUL 的"运行期可改的人格 seed"性质不同</li>
 *   <li>yml 化是半成品：白名单动态了，但 LC4J {@code @Tool}/{@code @P} 必须编译期常量，
 *       仍是字面值，维护两边反而冲突</li>
 *   <li>识别规则、directive、白名单全在 Java 代码内，单一来源，加字段只改一处</li>
 * </ul>
 *
 * <p>{@link #FIELDS} 是 LinkedHashMap，保留声明顺序——决定
 * {@code IdentityPromptSection} 渲染顺序 + promptInstructions 识别规则段顺序。
 * directive 中 {@code {}} 占位符由 hot value 替换（多处出现全部替换）。
 *
 * <p>加字段流程（v0.14+）：
 * <ol>
 *   <li>在 {@link #FIELDS} 静态块加一项 {@code m.put("xxx", new FieldSpec(directive, detectionRule))}</li>
 *   <li>更新 {@link #WHITELIST_LITERAL} 字面值，加新字段名（静态块末尾的 fail-fast 校验
 *       会保护这一步——漏改启动直接挂）</li>
 * </ol>
 *
 * <p>{@code UserImpressionUpdateTool} 的 {@code @P} 注解通过 {@code "..." + WHITELIST_LITERAL}
 * 编译期拼接，==无需手工同步==；{@code @Tool} 注解描述只列工具用途，不含字段白名单。
 */
public final class UserImpressionFields {

    /**
     * 单个字段的全部元信息。
     *
     * @param renderDirective 字段在「## 关于对方」段中的渲染模板（写后注入）。
     *                        {@code {}} 占位符多处出现时全部替换为 hot 值。
     * @param detectionRule   字段的识别与写入规则（用于 {@code UserImpressionUpdateTool}
     *                        的 promptInstructions 动态拼接，告诉 LLM 何时调
     *                        {@code update_user_impression(key, value)} 写此字段）
     */
    public record FieldSpec(String renderDirective, String detectionRule) {}

    /**
     * 白名单字面值（编译期常量，用于 {@code @P} 注解拼接）。
     *
     * <p>LC4J {@code @Tool} / {@code @P} 注解元素值必须是编译期常量——方法调用
     * （如 {@link #whitelistLiteral()}）即使返回常量字符串也==不算==编译期常量。
     * 字面字符串 + final 字段拼接才是编译期常量，因此这里维护一份 final 字面，
     * 供 {@code UserImpressionUpdateTool} 的 {@code @P} 直接拼接：
     *
     * <pre>{@code
     * @P("属性字段名。必须从白名单选：" + UserImpressionFields.WHITELIST_LITERAL) String key
     * }</pre>
     *
     * <p>这个字面==必须与 {@link #FIELDS} keys 一致==。静态初始化块在末尾做 fail-fast 校验，
     * 如果两者不一致会让类加载失败（保护"只改 FIELDS 忘改这里"的回归）。
     */
    public static final String WHITELIST_LITERAL = "username / language / role / interests / tone";

    /** 字段名 → FieldSpec。LinkedHashMap 保留声明顺序。 */
    public static final Map<String, FieldSpec> FIELDS;

    static {
        LinkedHashMap<String, FieldSpec> m = new LinkedHashMap<>();
        m.put("username", new FieldSpec(
                """
                        对方的称呼是「{}」。这是你已经认识的人——
                        - 直接称呼对方为「{}」，不要问名字、不要确认；
                        - 每轮回复中至少自然地使用一次这个称呼（除非对方明确要求别叫名字）；
                        - 如果对方问"我是谁""你还记得我吗"之类的问题，明确回答对方就是「{}」，
                          不要敷衍说"刚开始聊天""没有记录"。
                        """,
                "对方自我介绍 / 自报姓名时写入"
        ));
        m.put("language", new FieldSpec(
                """
                        对方使用的语言是 {}。请用同样的语言回复，除非对方明确要求切换。
                        """,
                "从对方当前消息识别其使用的语言；如果该字段尚未记录则立即写入；之后仅在对方明确要求切换（如\"改用英文回答\"）时更新"
        ));
        m.put("role", new FieldSpec(
                """
                        对方的身份/职业是 {}。讨论相关领域话题时可以自然地引用对方的专业视角，
                        不要刻意炫耀这一点。
                        """,
                "从对话中提到的工作 / 技术栈 / 行业线索整理出对方的身份/职业，写入一个简洁标签（如 \"Java 后端开发\"）"
        ));
        m.put("interests", new FieldSpec(
                """
                        对方的偏好/兴趣：{}。举例子时优先选这些领域。
                        """,
                "从对方主动提到的爱好 / 关注领域整理为简短标签集合"
        ));
        m.put("tone", new FieldSpec(
                """
                        对方期望的对话风格：{}。请按此风格调整回复。
                        """,
                "从对方对回复风格的反馈或要求（\"简洁点\"、\"详细点\"）整理写入"
        ));
        FIELDS = Collections.unmodifiableMap(m);

        // 启动 fail-fast：保护 WHITELIST_LITERAL 字面与 FIELDS keys 一致
        // 加字段时如果只改 FIELDS 忘了改 WHITELIST_LITERAL，类加载阶段就抛 IllegalStateException，
        // 而不是等到运行期 LLM 看到不一致的白名单才出诡异行为
        String dynamic = String.join(" / ", FIELDS.keySet());
        if (!WHITELIST_LITERAL.equals(dynamic)) {
            throw new IllegalStateException(
                    "UserImpressionFields.WHITELIST_LITERAL 与 FIELDS keys 不一致——加字段时两处都要改。\n"
                            + "  WHITELIST_LITERAL = \"" + WHITELIST_LITERAL + "\"\n"
                            + "  FIELDS keys join  = \"" + dynamic + "\""
            );
        }
    }

    /**
     * 拼接 {@code UserImpressionUpdateTool.promptInstructions()} 中的"各字段识别规则"段。
     * 按 FIELDS 声明顺序输出 {@code "- key —— detectionRule\n"} 多行。
     */
    public static String renderDetectionRules() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, FieldSpec> e : FIELDS.entrySet()) {
            sb.append("- ").append(e.getKey()).append(" —— ").append(e.getValue().detectionRule()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 拼接白名单字面（如 {@code "username / language / role / interests / tone"}），
     * 用于 {@code promptInstructions} 中的 "key 必须从以下白名单中选" 行。
     *
     * <p>与 {@link #WHITELIST_LITERAL} 等价（启动 fail-fast 校验保证）——
     * 此处仍保留方法形式给 {@code promptInstructions} 用 {@code .formatted()} 注入；
     * {@code @P} 注解因编译期常量约束必须用 {@link #WHITELIST_LITERAL} 字面。
     */
    public static String whitelistLiteral() {
        return WHITELIST_LITERAL;
    }

    private UserImpressionFields() {}
}
