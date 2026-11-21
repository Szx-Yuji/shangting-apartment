package com.yuji.apartment.dto;

import java.time.OffsetDateTime;

public record AdminAppointmentSlotResponse(
        Long id,
        Long roomId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int capacity,
        int reservedCount,
        int remainingCapacity
) {
}