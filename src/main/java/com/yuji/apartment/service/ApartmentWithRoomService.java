package com.yuji.apartment.service;

import com.yuji.apartment.dto.ApartmentResponse;
import com.yuji.apartment.dto.ApartmentWithRoomCreateRequest;
import com.yuji.apartment.dto.ApartmentWithRoomResponse;
import com.yuji.apartment.dto.RoomCreateRequest;
import com.yuji.apartment.dto.RoomResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApartmentWithRoomService {

    private final ApartmentService apartmentService;
    private final RoomService roomService;

    public ApartmentWithRoomService(
            ApartmentService apartmentService,
            RoomService roomService
    ) {
        this.apartmentService = apartmentService;
        this.roomService = roomService;
    }

    @Transactional
    public ApartmentWithRoomResponse create(
            ApartmentWithRoomCreateRequest request
    ) {
        ApartmentResponse apartment = apartmentService.create(
                request.apartment()
        );

        RoomCreateRequest roomRequest = new RoomCreateRequest(
                apartment.id(),
                request.roomNumber(),
                request.roomMonthlyRent()
        );

        RoomResponse room = roomService.create(roomRequest);

        return new ApartmentWithRoomResponse(apartment, room);
    }
}