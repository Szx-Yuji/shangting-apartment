package com.yuji.apartment.dto;

import java.math.BigDecimal;
import java.util.List;

public record FavoriteListingResponse(Long id, Long apartmentId, String apartmentName, String city,
        String address, String roomNumber, String title, Integer monthlyRent, BigDecimal area,
        Integer bedrooms, String orientation, String imageUrl, List<String> tags,
        boolean published, boolean available) {}
