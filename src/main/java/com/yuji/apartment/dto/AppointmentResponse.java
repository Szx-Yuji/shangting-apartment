package com.yuji.apartment.dto;

import java.time.OffsetDateTime;

public record AppointmentResponse(
        Long id,
        Long roomId,
        String contactName,
        String contactPhone,
        OffsetDateTime appointmentTime,
        String status,
        OffsetDateTime createdAt,
        String roomNumber,
        String apartmentName
) {
    @Override
    public String toString() {
        return "AppointmentResponse[contact=REDACTED]";
    }
}