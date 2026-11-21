package com.yuji.apartment.dto;

public record RoomDetailResponse(
        Long id,
        Long apartmentId,
        String apartmentName,
        String city,
        String roomNumber,
        Integer monthlyRent,
        Boolean published
) {
}