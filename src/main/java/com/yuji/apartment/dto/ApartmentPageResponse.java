package com.yuji.apartment.dto;

import java.util.List;

public record ApartmentPageResponse(
        List<ApartmentResponse> records,
        long total,
        long page,
        long pageSize
) {}