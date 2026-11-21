package com.yuji.apartment.controller;

import com.yuji.apartment.dto.ApartmentResponse;
import com.yuji.apartment.service.ApartmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.yuji.apartment.dto.ApartmentCreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import com.yuji.apartment.dto.ApartmentPageResponse;
import org.springframework.web.server.ResponseStatusException;
import com.yuji.apartment.dto.ApartmentUpdateRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
@RequestMapping("/api/apartments")
public class ApartmentController {

    private final ApartmentService apartmentService;

    public ApartmentController(ApartmentService apartmentService) {
        this.apartmentService = apartmentService;
    }

    @GetMapping
    public ApartmentPageResponse getAll(
            @RequestParam(name = "city", required = false)
            String city,

            @RequestParam(name = "page", defaultValue = "1")
            long page,

            @RequestParam(name = "pageSize", defaultValue = "10")
            long pageSize
    ) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "页码必须大于等于1，每页条数必须在1到100之间"
            );
        }

        return apartmentService.findPage(city, page, pageSize);
    }

    @GetMapping("/{id}")
    public ApartmentResponse getById(
            @PathVariable("id") Long id
    ) {
        return apartmentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApartmentResponse create(
            @Valid @RequestBody ApartmentCreateRequest request
    ) {
        return apartmentService.create(request);
    }

    @PutMapping("/{id}")
    public ApartmentResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ApartmentUpdateRequest request
    ) {
        return apartmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") Long id
    ) {
        apartmentService.delete(id);
    }
}