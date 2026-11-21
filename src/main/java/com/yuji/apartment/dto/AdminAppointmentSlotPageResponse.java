package com.yuji.apartment.dto;

import java.util.List;

public record AdminAppointmentSlotPageResponse(
        List<AdminAppointmentSlotResponse> records,
        long total,
        long page,
        long pageSize
) {
}