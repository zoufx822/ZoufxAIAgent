package com.zoufx.ai.agent.chat.model;

import jakarta.validation.constraints.NotBlank;

public record AnchorTitleUpdateRequest(
        @NotBlank(message = "不能为空") String title) {
}
