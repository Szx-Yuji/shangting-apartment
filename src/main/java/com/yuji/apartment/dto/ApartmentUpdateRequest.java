package com.yuji.apartment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ApartmentUpdateRequest(
        @NotBlank(message = "公寓名称不能为空")
        @Size(max = 100, message = "公寓名称不能超过100个字符")
        String name,

        @NotBlank(message = "城市不能为空")
        @Size(max = 50, message = "城市不能超过50个字符")
        String city,

        @NotNull(message = "月租金不能为空")
        @Positive(message = "月租金必须大于0")
        Integer monthlyRent
) {}