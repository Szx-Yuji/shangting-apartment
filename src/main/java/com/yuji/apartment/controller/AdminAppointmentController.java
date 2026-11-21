package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AppointmentPageResponse;
import com.yuji.apartment.service.AppointmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.yuji.apartment.dto.AppointmentStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.service.AppointmentReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import com.yuji.apartment.dto.AppointmentReviewResponse;
import com.yuji.apartment.dto.AppointmentQueryRequest;
import org.springframework.web.bind.annotation.ModelAttribute;

@RestController
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentReviewService reviewService;

    public AdminAppointmentController(
            AppointmentService appointmentService,
            AppointmentReviewService reviewService
    ) {
        this.appointmentService = appointmentService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public AppointmentPageResponse getPage(
            @Valid @ModelAttribute AppointmentQueryRequest request
    ) {
        return appointmentService.findPage(request);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody AppointmentStatusUpdateRequest request,
            @AuthenticationPrincipal AdminAccountResponse currentAdmin
    ) {
        if (currentAdmin == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }

        reviewService.review(
                id,
                request,
                currentAdmin.id()
        );
    }

    @GetMapping("/{id}/review-log")
    public AppointmentReviewResponse getReviewLog(
            @PathVariable("id") Long id
    ) {
        return reviewService.findByAppointmentId(id);
    }
}