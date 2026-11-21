package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名不能超过50个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码长度不能超过72个字符")
        String password
) {

    @Override
    public String toString() {
        return "AdminLoginRequest[credentials=REDACTED]";
    }
}