package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AppointmentSlotPageResponse;
import com.yuji.apartment.service.AppointmentSlotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/rooms/{roomId}/appointment-slots")
public class PublicAppointmentSlotController {

    private final AppointmentSlotService appointmentSlotService;

    public PublicAppointmentSlotController(
            AppointmentSlotService appointmentSlotService
    ) {
        this.appointmentSlotService = appointmentSlotService;
    }

    @GetMapping
    public AppointmentSlotPageResponse getPage(
            @PathVariable("roomId") Long roomId,

            @RequestParam(name = "page", defaultValue = "1")
            long page,

            @RequestParam(name = "pageSize", defaultValue = "10")
            long pageSize
    ) {
        return appointmentSlotService.findAvailablePage(
                roomId,
                page,
                pageSize
        );
    }
}