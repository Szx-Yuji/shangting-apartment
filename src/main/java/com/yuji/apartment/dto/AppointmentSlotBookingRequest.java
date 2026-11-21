package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AppointmentSlotBookingRequest(
        @NotNull(message = "预约时段ID不能为空")
        @Positive(message = "预约时段ID必须大于0")
        Long slotId,

        @NotBlank(message = "联系人不能为空")
        @Size(max = 50, message = "联系人不能超过50个字符")
        String contactName,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(
                regexp = "^1[3-9]\\d{9}$",
                message = "请输入有效的中国大陆手机号"
        )
        String contactPhone
) {
    @Override
    public String toString() {
        return "AppointmentSlotBookingRequest[contact=REDACTED]";
    }
}