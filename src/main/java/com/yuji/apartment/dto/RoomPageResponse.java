package com.yuji.apartment.dto;

import java.util.List;

public record RoomPageResponse(
        List<RoomResponse> records,
        long total,
        long page,
        long pageSize
) {}