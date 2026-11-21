package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentCreateResponse;
import com.yuji.apartment.dto.AppointmentSlotBookingRequest;
import com.yuji.apartment.entity.AppointmentRequest;
import com.yuji.apartment.mapper.AppointmentRequestMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AppointmentRequestService {
    private final AppointmentRequestMapper requestMapper;
    private final AppointmentService appointmentService;

    public AppointmentRequestService(
            AppointmentRequestMapper requestMapper,
            AppointmentService appointmentService
    ) {
        this.requestMapper = requestMapper;
        this.appointmentService = appointmentService;
    }

    @Transactional
    public AppointmentCreateResponse bookOnce(
            String requestKey,
            String requestHash,
            AppointmentSlotBookingRequest request
    ) {
        return bookOnce(requestKey, requestHash, request, null);
    }

    @Transactional
    public AppointmentCreateResponse bookOnce(String requestKey, String requestHash,
                                              AppointmentSlotBookingRequest request, Long userId) {
        AppointmentRequest record = new AppointmentRequest();
        record.setRequestKey(requestKey);
        record.setRequestHash(requestHash);
        record.setCreatedAt(
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        int inserted = requestMapper.insert(record);

        if (inserted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "预约请求登记失败"
            );
        }

        AppointmentCreateResponse response =
                appointmentService.book(request, userId);

        record.setAppointmentId(response.id());

        int updated = requestMapper.updateById(record);

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "预约结果保存失败"
            );
        }

        return response;
    }
}
