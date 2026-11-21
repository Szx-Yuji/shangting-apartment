package com.yuji.apartment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

public record AppointmentQueryRequest(

        @Pattern(
                regexp = "^(PENDING|CONFIRMED|REJECTED|CANCELLED)$",
                message = "status只支持PENDING、CONFIRMED、REJECTED、CANCELLED"
        )
        String status,

        @Positive(message = "房间ID必须大于0")
        Long roomId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime startTime,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime endTime,

        @Positive(message = "页码必须大于0")
        Long page,

        @Positive(message = "每页条数必须大于0")
        @Max(value = 100, message = "每页条数不能超过100")
        Long pageSize
) {
    public AppointmentQueryRequest {
        if (status != null) {
            status = status.strip();

            if (status.isEmpty()) {
                status = null;
            }
        }

        if (page == null) {
            page = 1L;
        }

        if (pageSize == null) {
            pageSize = 10L;
        }
    }
}
