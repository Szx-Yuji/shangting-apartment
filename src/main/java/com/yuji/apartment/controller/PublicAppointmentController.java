package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AppointmentCreateResponse;
import com.yuji.apartment.dto.AppointmentSlotBookingRequest;
import com.yuji.apartment.ratelimit.AppointmentRateLimiter;
import com.yuji.apartment.service.AppointmentBookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/appointments")
public class PublicAppointmentController {

    private final AppointmentBookingService bookingService;
    private final AppointmentRateLimiter rateLimiter;

    public PublicAppointmentController(
            AppointmentBookingService bookingService,
            AppointmentRateLimiter rateLimiter
    ) {
        this.bookingService = bookingService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentCreateResponse create(
            @RequestHeader("Idempotency-Key") String requestKey,
            @Valid @RequestBody AppointmentSlotBookingRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();

        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "请求过于频繁，请稍后再试"
            );
        }

        return bookingService.book(requestKey, request);
    }
}