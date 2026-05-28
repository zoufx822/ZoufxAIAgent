package com.zoufx.ai.agent.chat.impl;

import com.zoufx.ai.agent.chat.api.PromptSection;
import com.zoufx.ai.agent.memory.api.HotMemoryStore;
import com.zoufx.ai.agent.memory.support.HotMemoryType;
import com.zoufx.ai.agent.memory.support.UserImpressionFields;
import com.zoufx.ai.agent.memory.support.UserImpressionFields.FieldSpec;
import com.zoufx.ai.agent.memory.property.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 「## 你对对方的了解程度」段（order=26）——基于画像完备度引导主动了解。
 *
 * <p>按 fill_ratio 落入三档：stranger（ratio &lt; 0.3 → 追问一个字段 + 深度话题拒答）、
 * half-known（0.3~0.7 → 适度追问 + 保留意见）、fully-known（≥ 0.7 → 不追问，引用已知）。
 * stranger 模式下按 FIELDS 声明顺序找未填字段第 1 名，动态拼接追问指令替换
 * {@code {fieldQuestion}} 占位。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImpressionGuidanceSection implements PromptSection {

    private static final String SECTION_HEADER = "## 你对对方的了解程度\n\n";
    private static final String STRANGER = "stranger";
    private static final String HALF_KNOWN = "half-known";
    private static final String FULLY_KNOWN = "fully-known";
    private static final String FIELD_QUESTION_PLACEHOLDER = "{fieldQuestion}";

    static final String STRANGER_PROMPT = """
            你对对方几乎了解不足。在本轮回复里：
            - ==优先==：若对方本轮主动提供了任何画像信息（自报名字、职业、爱好等），立刻调 update_user_impression 写入，再继续回复——不能只口头确认而不调工具
            - 先正面回应对方主线诉求（不打断、不绕题）
            - 然后自然地引一次问对方的某个画像信息（具体问哪个由系统注入：{fieldQuestion}）
            - 如果 chat memory 显示你之前已经问过该字段且对方没正面答，或对方明确拒绝回答（"不用问""没必要"等），==别再追问==
            - 反模式：明明该问却通篇绕过、完全不引向认识——是错的，至少引一次
            - 如果对方提出"个性化判断 / 情感建议 / 人生选择 / 投资建议"等深度话题，
              礼貌说明"我们刚认识不久，我想先了解你一些，再给意见会更靠谱"，然后顺势抛出问题
            """;
    static final String HALF_KNOWN_PROMPT = """
            你已经认识对方一段时间，但对其内在仍不够了解。在合适的时机：
            - ==优先==：若对方本轮主动提供了任何画像信息，立刻调 update_user_impression 写入，再继续回复——不能只口头确认而不调工具
            - 自然地穿插一句追问，但不要每轮都问，每 2~3 轮一次
            - 当对方分享细节时，主动调 update_user_impression 写入推断到的字段
            - 深度话题可以尝试给意见，但要明示"基于我目前对你的了解"
            """;
    static final String FULLY_KNOWN_PROMPT = """
            你已经较充分地了解对方。在回答时：
            - 使用对方习惯的称呼与沟通风格
            - 个性化判断 / 建议时显式引用你对对方的认知（如"你之前提过你做事偏谨慎"）
            - 仍在对话中持续观察新信号，必要时 update_user_impression 补充或修正
            """;

    private final HotMemoryStore hotMemoryStore;
    private final MemoryProperties properties;

    @Override
    public int order() {
        return 26;
    }

    @Override
    @Nullable
    public String render(@Nullable String userId, @Nullable String anchorId) {
        if (userId == null) return null;
        Map<String, String> snap = hotMemoryStore.snapshot(userId, HotMemoryType.USER_IMPRESSION);

        int total = UserImpressionFields.FIELDS.size();
        if (total == 0) return null;
        int filled = 0;
        for (String key : UserImpressionFields.FIELDS.keySet()) {
            String v = snap.get(key);
            if (v != null && !v.isBlank()) filled++;
        }
        double fillRatio = (double) filled / total;

        String mode = resolveMode(fillRatio);
        String template = promptForMode(mode);
        if (template == null) {
            log.warn("ImpressionGuidanceSection skipped: no prompt for mode={} (fillRatio={})",
                    mode, fillRatio);
            return null;
        }

        String rendered = template;
        if (STRANGER.equals(mode) && rendered.contains(FIELD_QUESTION_PLACEHOLDER)) {
            rendered = rendered.replace(FIELD_QUESTION_PLACEHOLDER, buildFieldQuestion(snap));
        }
        return SECTION_HEADER + rendered;
    }

    private String promptForMode(String mode) {
        return switch (mode) {
            case STRANGER -> STRANGER_PROMPT;
            case HALF_KNOWN -> HALF_KNOWN_PROMPT;
            case FULLY_KNOWN -> FULLY_KNOWN_PROMPT;
            default -> null;
        };
    }

    private String resolveMode(double fillRatio) {
        MemoryProperties.Completeness c = properties.getHot().getUserImpression().getCompleteness();
        if (fillRatio < c.getStrangerThreshold()) return STRANGER;
        if (fillRatio < c.getFullyKnownThreshold()) return HALF_KNOWN;
        return FULLY_KNOWN;
    }

    /**
     * 按 FIELDS 声明顺序找未填字段第 1 名，拼追问指令。
     *
     * <p>仅在 stranger mode 下调（fillRatio < 0.3 → 至少 70% 字段未填，循环必命中）。
     * 若配置错误导致找不到未填字段，抛 {@link IllegalStateException} fail-fast 而非塞空串。
     */
    private String buildFieldQuestion(Map<String, String> snap) {
        for (Map.Entry<String, FieldSpec> e : UserImpressionFields.FIELDS.entrySet()) {
            String v = snap.get(e.getKey());
            if (v == null || v.isBlank()) {
                return "本轮请自然地引一次，问对方的" + e.getValue().nameForUser();
            }
        }
        throw new IllegalStateException(
                "ImpressionGuidanceSection.buildFieldQuestion 在 stranger mode 下未找到未填字段——"
                        + "请检查 ai.memory.hot.user-impression.completeness.stranger-threshold 配置是否异常");
    }
}
