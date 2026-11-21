package com.yuji.apartment.dto;

public record ApartmentResponse(
        Long id,
        String name,
        String city,
        Integer monthlyRent
) {
}
