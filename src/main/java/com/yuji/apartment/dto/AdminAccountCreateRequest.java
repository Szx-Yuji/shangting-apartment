package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminAccountCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,50}", message = "用户名须为3到50位字母、数字或下划线") String username,
        @NotBlank @Size(min = 12, max = 72, message = "密码长度须为12到72个字符") String password
) {
    @Override public String toString() { return "AdminAccountCreateRequest[credentials=REDACTED]"; }
}
