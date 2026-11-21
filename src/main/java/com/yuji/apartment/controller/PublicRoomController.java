package com.yuji.apartment.controller;

import com.yuji.apartment.dto.RoomDetailResponse;
import com.yuji.apartment.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.yuji.apartment.dto.RoomPageResponse;
import com.yuji.apartment.dto.RoomQueryRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;

@RestController
@RequestMapping("/api/public/rooms")
public class PublicRoomController {

    private final RoomService roomService;

    public PublicRoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/{id}")
    public RoomDetailResponse getDetail(@PathVariable("id") Long id) {
        if (id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        return roomService.findPublishedDetailById(id);
    }

    @GetMapping
    public RoomPageResponse getPage(
            @Valid @ModelAttribute RoomQueryRequest request
    ) {
        return roomService.findPublishedPage(request);
    }
}