package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AppointmentSlotCreateRequest;
import com.yuji.apartment.dto.AppointmentSlotCreateResponse;
import com.yuji.apartment.service.AppointmentSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.yuji.apartment.dto.AdminAppointmentSlotPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/admin/appointment-slots")
public class AdminAppointmentSlotController {

    private final AppointmentSlotService appointmentSlotService;

    public AdminAppointmentSlotController(
            AppointmentSlotService appointmentSlotService
    ) {
        this.appointmentSlotService = appointmentSlotService;
    }

    @GetMapping
    public AdminAppointmentSlotPageResponse getPage(
            @RequestParam(name = "roomId")
            Long roomId,

            @RequestParam(name = "page", defaultValue = "1")
            long page,

            @RequestParam(name = "pageSize", defaultValue = "10")
            long pageSize
    ) {
        return appointmentSlotService.findAdminPage(
                roomId,
                page,
                pageSize
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentSlotCreateResponse create(
            @Valid @RequestBody AppointmentSlotCreateRequest request
    ) {
        Long id = appointmentSlotService.create(request);

        return new AppointmentSlotCreateResponse(id);
    }
}