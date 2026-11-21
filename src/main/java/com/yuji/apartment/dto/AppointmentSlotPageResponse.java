package com.yuji.apartment.dto;

import java.util.List;

public record AppointmentSlotPageResponse(
        List<AppointmentSlotResponse> records,
        long total,
        long page,
        long pageSize
) {
}