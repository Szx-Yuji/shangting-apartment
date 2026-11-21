package com.yuji.apartment.dto;

public record RoomRentStatsResponse(
        long roomCount,
        Integer minRent,
        Integer maxRent
) {}