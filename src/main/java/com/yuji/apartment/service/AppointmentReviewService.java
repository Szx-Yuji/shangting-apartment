package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentStatusUpdateRequest;
import com.yuji.apartment.entity.AppointmentReviewLog;
import com.yuji.apartment.mapper.AppointmentReviewLogMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuji.apartment.dto.AppointmentReviewResponse;
import com.yuji.apartment.monitor.TimedOperation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AppointmentReviewService {

    private final AppointmentService appointmentService;
    private final AppointmentReviewLogMapper reviewLogMapper;

    public AppointmentReviewService(
            AppointmentService appointmentService,
            AppointmentReviewLogMapper reviewLogMapper
    ) {
        this.appointmentService = appointmentService;
        this.reviewLogMapper = reviewLogMapper;
    }

    @TimedOperation
    @Transactional
    public void review(
            Long appointmentId,
            AppointmentStatusUpdateRequest request,
            Long adminId
    ) {
        if (adminId == null || adminId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }

        appointmentService.updateStatus(appointmentId, request);

        AppointmentReviewLog record = new AppointmentReviewLog();
        record.setAppointmentId(appointmentId);
        record.setAdminId(adminId);
        record.setFromStatus("PENDING");
        record.setToStatus(request.status());
        record.setCreatedAt(
                LocalDateTime.now(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MICROS)
        );

        int inserted = reviewLogMapper.insert(record);

        if (inserted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "审核记录保存失败"
            );
        }
    }

    @TimedOperation
    public AppointmentReviewResponse findByAppointmentId(
            Long appointmentId
    ) {
        if (appointmentId == null || appointmentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预约ID必须大于0"
            );
        }

        AppointmentReviewLog record = reviewLogMapper.selectOne(
                new LambdaQueryWrapper<AppointmentReviewLog>()
                        .eq(
                                AppointmentReviewLog::getAppointmentId,
                                appointmentId
                        )
        );

        if (record == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "该预约暂无审核记录"
            );
        }

        return new AppointmentReviewResponse(
                record.getId(),
                record.getAppointmentId(),
                record.getAdminId(),
                record.getFromStatus(),
                record.getToStatus(),
                record.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}