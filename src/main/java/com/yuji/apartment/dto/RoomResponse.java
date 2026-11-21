package com.yuji.apartment.dto;

public record RoomResponse(
        Long id,
        Long apartmentId,
        String roomNumber,
        Integer monthlyRent,
        Boolean published
) {
}