package com.yuji.apartment.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** API DTOs: database entities and internal columns never leave this boundary. */
public final class CatalogDtos {
    private CatalogDtos() {}
    public record Page<T>(List<T> records, long total, long page, long pageSize) {}
    public record Listing(Long id, Long apartmentId, String apartmentName, String city, String address,
                          String roomNumber, String title, int monthlyRent, BigDecimal area,
                          int bedrooms, String orientation, String imageUrl, List<String> tags,
                          boolean published, boolean available, String description) {}
    public record Apartment(Long id, String name, String city, int monthlyRent, String address,
                            String description, String imageUrl, long roomCount) {}
    public record ApartmentInput(
            @NotBlank @Size(max=100) String name,
            @NotBlank @Size(max=50) String city,
            @NotNull @Positive Integer monthlyRent,
            @NotBlank @Size(max=200) String address,
            @Size(max=5000) String description,
            @Size(max=300) String imageUrl) {}
    public record RoomInput(
            @NotNull @Positive Long apartmentId,
            @NotBlank @Size(max=20) String roomNumber,
            @NotBlank @Size(max=120) String title,
            @NotNull @Positive Integer monthlyRent,
            @NotNull @DecimalMin("1.0") @DecimalMax("1000.0") BigDecimal area,
            @Min(0) @Max(20) int bedrooms,
            @NotBlank @Size(max=20) String orientation,
            @Size(max=5000) String description,
            @Size(max=300) String imageUrl,
            @Size(max=12) List<@NotBlank @Size(max=20) String> tags,
            boolean published) {}
    public record ImageUpload(String url) {}
}
