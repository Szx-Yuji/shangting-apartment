package com.yuji.apartment.dto;

import java.time.LocalDateTime;

public record AppointmentListRow(
        Long id,
        Long roomId,
        String roomNumber,
        String apartmentName,
        String contactName,
        String contactPhone,
        LocalDateTime appointmentTime,
        String status,
        LocalDateTime createdAt
) {
    @Override
    public String toString() {
        return "AppointmentListRow[contact=REDACTED]";
    }
}