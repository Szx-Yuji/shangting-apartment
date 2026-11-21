package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AppointmentStatusUpdateRequest(
        @NotBlank(message = "预约状态不能为空")
        @Pattern(
                regexp = "^(CONFIRMED|REJECTED)$",
                message = "预约状态只支持CONFIRMED或REJECTED"
        )
        String status
) {
}