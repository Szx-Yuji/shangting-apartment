package com.yuji.apartment.controller;

import com.yuji.apartment.dto.ApartmentPageResponse;
import com.yuji.apartment.service.PublicApartmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/apartments")
public class PublicApartmentController {
    private final PublicApartmentService publicApartmentService;

    public PublicApartmentController(
            PublicApartmentService publicApartmentService
    ) {
        this.publicApartmentService = publicApartmentService;
    }

    @GetMapping
    public ApartmentPageResponse getPage(
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize
    ) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "页码必须大于等于1，每页条数必须在1到100之间"
            );
        }

        return publicApartmentService.findPage(city, page, pageSize);
    }
}