package com.yuji.apartment.controller;

import com.yuji.apartment.dto.*;
import com.yuji.apartment.ratelimit.AppointmentRateLimiter;
import com.yuji.apartment.service.AppointmentBookingService;
import com.yuji.apartment.service.MyAppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/appointments")
public class MyAppointmentController {
    private final MyAppointmentService appointments;
    private final AppointmentBookingService booking;
    private final AppointmentRateLimiter limiter;
    public MyAppointmentController(MyAppointmentService appointments, AppointmentBookingService booking, AppointmentRateLimiter limiter) {
        this.appointments = appointments; this.booking = booking; this.limiter = limiter;
    }

    @GetMapping
    public AppointmentPageResponse page(@AuthenticationPrincipal UserAccountResponse user,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long pageSize) {
        return appointments.findPage(user.id(), page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentCreateResponse create(@AuthenticationPrincipal UserAccountResponse user,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AppointmentSlotBookingRequest body,
            HttpServletRequest request) {
        if (!limiter.tryAcquire(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "预约请求过于频繁，请稍后再试");
        }
        return booking.book(key, body, user.id());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal UserAccountResponse user, @PathVariable Long id) {
        appointments.cancel(user.id(), id);
    }
}
