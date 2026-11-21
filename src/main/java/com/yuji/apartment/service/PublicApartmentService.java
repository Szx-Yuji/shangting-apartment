package com.yuji.apartment.service;

import com.yuji.apartment.cache.ApartmentListCache;
import com.yuji.apartment.dto.ApartmentPageResponse;
import org.springframework.stereotype.Service;

@Service
public class PublicApartmentService {
    private final ApartmentService apartmentService;
    private final ApartmentListCache apartmentListCache;

    public PublicApartmentService(
            ApartmentService apartmentService,
            ApartmentListCache apartmentListCache
    ) {
        this.apartmentService = apartmentService;
        this.apartmentListCache = apartmentListCache;
    }

    public ApartmentPageResponse findPage(
            String city,
            long page,
            long pageSize
    ) {
        String normalizedCity = city == null ? "" : city.strip();

        String key = "public:apartments:v1:city:" + normalizedCity
                + ":page:" + page
                + ":pageSize:" + pageSize;

        ApartmentPageResponse cached = apartmentListCache.get(key);

        if (cached != null) {
            return cached;
        }

        ApartmentPageResponse result = apartmentService.findPage(
                normalizedCity, page, pageSize
        );

        apartmentListCache.put(key, result);

        return result;
    }
}