package com.yuji.apartment.controller;

import com.yuji.apartment.dto.RoomCreateRequest;
import com.yuji.apartment.dto.RoomResponse;
import com.yuji.apartment.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.yuji.apartment.dto.RoomPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import com.yuji.apartment.dto.RoomUpdateRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.yuji.apartment.dto.RoomQueryRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.yuji.apartment.dto.RoomCountResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.yuji.apartment.dto.RoomRentStatsResponse;
import com.yuji.apartment.dto.RoomDetailResponse;
import com.yuji.apartment.dto.RoomPublicationRequest;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(
            @Valid @RequestBody RoomCreateRequest request
    ) {
        return roomService.create(request);
    }

    @GetMapping
    public RoomPageResponse getPage(
            @Valid @ModelAttribute RoomQueryRequest request
    ) {
        return roomService.findPage(request);
    }

    @GetMapping("/count")
    public RoomCountResponse count(
            @RequestParam("apartmentId") Long apartmentId
    ) {
        if (apartmentId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "公寓ID必须大于0"
            );
        }

        return roomService.countByApartmentId(apartmentId);
    }

    @GetMapping("/rent-stats")
    public RoomRentStatsResponse getRentStats(
            @RequestParam("apartmentId") Long apartmentId
    ) {
        if (apartmentId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "公寓ID必须大于0"
            );
        }

        return roomService.getRentStats(apartmentId);
    }

    @GetMapping("/{id}")
    public RoomResponse getById(
            @PathVariable("id") Long id
    ) {
        if (id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        return roomService.findById(id);
    }

    @GetMapping("/{id}/detail")
    public RoomDetailResponse getDetail(
            @PathVariable("id") Long id
    ) {
        if (id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        return roomService.findDetailById(id);
    }

    @PutMapping("/{id}")
    public RoomResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoomUpdateRequest request
    ) {
        if (id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        return roomService.update(id, request);
    }

    @PatchMapping("/{id}/publication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePublication(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoomPublicationRequest request
    ) {
        if (id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        roomService.updatePublication(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") Long id
    ) {
        if (id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        roomService.delete(id);
    }
}