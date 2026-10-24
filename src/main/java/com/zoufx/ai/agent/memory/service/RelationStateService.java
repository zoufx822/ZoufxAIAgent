package com.zoufx.ai.agent.memory.service;

import com.zoufx.ai.agent.affect.model.Affect;
import com.zoufx.ai.agent.affect.model.Emotion;
import com.zoufx.ai.agent.memory.api.RelationStateDao;
import com.zoufx.ai.agent.memory.model.RelationState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * 关系状态的累计逻辑——把每轮的用户情绪折算成熟悉度/信任/基调的慢变化。
 * 同步签名：调用方 {@code ChatService.persistTurn} 在事务内调用。
 */
@Service
@RequiredArgsConstructor
public class RelationStateService {

    private final RelationStateDao dao;

    /** 应用一轮用户情绪，返回更新后的状态并落库；无情绪时只推进更新时间。 */
    public RelationState apply(String userId, @Nullable Affect affect) {
        RelationState old = dao.load(userId);
        double familiarity = old.familiarity();
        double trust = old.trust();
        double warmth = old.warmth();

        if (affect != null) {
            familiarity = Math.min(1, familiarity + Math.min(0.03, 0.005 + affect.intensity() * 0.003));
            if (isVulnerable(affect) && affect.intensity() >= 3) {
                trust = Math.min(1, trust + 0.02);
            }
            warmth = Math.max(-1, Math.min(1, 0.85 * warmth + 0.15 * valence(affect.emotion())));
        }

        RelationState next = new RelationState(familiarity, trust, warmth, System.currentTimeMillis());
        dao.save(userId, next);
        return next;
    }

    private static boolean isVulnerable(Affect affect) {
        return affect.emotion() == Emotion.SAD || affect.emotion() == Emotion.ANGRY;
    }

    private static double valence(Emotion emotion) {
        return switch (emotion) {
            case HAPPY -> 1.0;
            case CURIOUS -> 0.3;
            case CONFUSED -> -0.2;
            case SAD -> -0.6;
            case ANGRY -> -0.8;
            default -> 0.0;
        };
    }
}
