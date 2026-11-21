package com.yuji.apartment.dto;

import java.time.OffsetDateTime;

public record AppointmentReviewResponse(
        Long id,
        Long appointmentId,
        Long adminId,
        String fromStatus,
        String toStatus,
        OffsetDateTime createdAt
) {
}