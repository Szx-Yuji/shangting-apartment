// src/main/java/com/yuji/apartment/dto/AppointmentPageResponse.java
package com.yuji.apartment.dto;

import java.util.List;

public record AppointmentPageResponse(
        List<AppointmentResponse> records,
        long total,
        long page,
        long pageSize
) {
}