package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentCreateResponse;
import com.yuji.apartment.dto.AppointmentSlotBookingRequest;
import com.yuji.apartment.entity.AppointmentRequest;
import com.yuji.apartment.mapper.AppointmentRequestMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.yuji.apartment.monitor.TimedOperation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentBookingService {
    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final AppointmentRequestMapper requestMapper;
    private final AppointmentRequestService requestService;
    private final ObjectMapper objectMapper;

    public AppointmentBookingService(
            AppointmentRequestMapper requestMapper,
            AppointmentRequestService requestService,
            ObjectMapper objectMapper
    ) {
        this.requestMapper = requestMapper;
        this.requestService = requestService;
        this.objectMapper = objectMapper;
    }

    private String normalizeKey(String key) {
        if (key == null || !key.matches(UUID_PATTERN)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key 必须是标准 UUID"
            );
        }

        return UUID.fromString(key).toString();
    }

    private String calculateHash(AppointmentSlotBookingRequest request, Long userId) {
        try {
            byte[] content = objectMapper.writeValueAsBytes(
                    List.of(
                            userId == null ? "guest" : "user:" + userId,
                            request.slotId(),
                            request.contactName().strip(),
                            request.contactPhone()
                    )
            );

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "预约请求处理失败"
            );
        }
    }

    private AppointmentCreateResponse replay(
            AppointmentRequest record,
            String requestHash
    ) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "同一个请求标识不能用于不同的预约内容"
            );
        }

        if (record.getAppointmentId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "预约请求尚未完成，请稍后使用相同标识重试"
            );
        }

        return new AppointmentCreateResponse(
                record.getAppointmentId(), "PENDING"
        );
    }

    @TimedOperation
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppointmentCreateResponse book(
            String requestKey,
            AppointmentSlotBookingRequest request
    ) {
        return book(requestKey, request, null);
    }

    @TimedOperation
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppointmentCreateResponse book(String requestKey, AppointmentSlotBookingRequest request, Long userId) {
        String key = normalizeKey(requestKey);
        String hash = calculateHash(request, userId);

        try {
            return requestService.bookOnce(key, hash, request, userId);
        } catch (DuplicateKeyException exception) {
            AppointmentRequest completed = requestMapper.selectById(key);

            if (completed == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "预约提交失败，请稍后使用相同标识重试"
                );
            }

            return replay(completed, hash);
        }
    }
}
