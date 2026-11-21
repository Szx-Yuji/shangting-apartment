package com.yuji.apartment.dto;

public record RoomCountResponse(
        Long apartmentId,
        long roomCount
) {}