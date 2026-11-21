package com.yuji.apartment.dto;

import java.time.OffsetDateTime;

public record AppointmentSlotResponse(
        Long id,
        Long roomId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int remainingCapacity
) {
}