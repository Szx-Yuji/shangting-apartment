package com.yuji.apartment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ApartmentWithRoomCreateRequest(
        @NotNull(message = "公寓信息不能为空")
        @Valid
        ApartmentCreateRequest apartment,

        @NotBlank(message = "房间号不能为空")
        @Size(max = 20, message = "房间号不能超过20个字符")
        String roomNumber,

        @NotNull(message = "房间月租金不能为空")
        @Positive(message = "房间月租金必须大于0")
        Integer roomMonthlyRent
) {}