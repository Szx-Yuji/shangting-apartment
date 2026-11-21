package com.yuji.apartment.dto;

public record ApartmentWithRoomResponse(
        ApartmentResponse apartment,
        RoomResponse room
) {}