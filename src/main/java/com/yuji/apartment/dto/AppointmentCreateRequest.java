package com.yuji.apartment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AppointmentCreateRequest(
        @NotNull(message = "房间ID不能为空")
        @Positive(message = "房间ID必须大于0")
        Long roomId,

        @NotBlank(message = "联系人不能为空")
        @Size(max = 50, message = "联系人不能超过50个字符")
        String contactName,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(
                regexp = "^1[3-9]\\d{9}$",
                message = "请输入有效的中国大陆手机号"
        )
        String contactPhone,

        @NotNull(message = "预约时间不能为空")
        @Future(message = "预约时间必须晚于当前时间")
        OffsetDateTime appointmentTime
) {
    @Override
    public String toString() {
        return "AppointmentCreateRequest[contact=REDACTED]";
    }
}