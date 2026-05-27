package com.zoufx.ai.agent.chat.model;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

public record AnchorCreateRequest(
        @NotBlank(message = "不能为空") String userId,
        @Nullable String title) {
}
