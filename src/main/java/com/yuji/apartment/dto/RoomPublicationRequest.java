package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotNull;

public record RoomPublicationRequest(
        @NotNull(message = "上架状态不能为空")
        Boolean published
) {
}