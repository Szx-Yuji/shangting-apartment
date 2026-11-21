package com.yuji.apartment.controller;

import com.yuji.apartment.dto.ApartmentWithRoomCreateRequest;
import com.yuji.apartment.dto.ApartmentWithRoomResponse;
import com.yuji.apartment.service.ApartmentWithRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apartments/with-room")
public class ApartmentWithRoomController {

    private final ApartmentWithRoomService apartmentWithRoomService;

    public ApartmentWithRoomController(
            ApartmentWithRoomService apartmentWithRoomService
    ) {
        this.apartmentWithRoomService = apartmentWithRoomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApartmentWithRoomResponse create(
            @Valid @RequestBody ApartmentWithRoomCreateRequest request
    ) {
        return apartmentWithRoomService.create(request);
    }
}