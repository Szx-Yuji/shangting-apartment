package com.yuji.apartment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record AppointmentSlotCreateRequest(
        @NotNull(message = "房间ID不能为空")
        @Positive(message = "房间ID必须大于0")
        Long roomId,

        @NotNull(message = "开始时间不能为空")
        @Future(message = "开始时间必须晚于当前时间")
        OffsetDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        OffsetDateTime endTime,

        @NotNull(message = "预约容量不能为空")
        @Positive(message = "预约容量必须大于0")
        Integer capacity
) {
}