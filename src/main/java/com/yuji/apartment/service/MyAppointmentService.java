package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentPageResponse;
import com.yuji.apartment.dto.AppointmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MyAppointmentService {
    private final JdbcClient jdbc;
    public MyAppointmentService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public AppointmentPageResponse findPage(Long userId, long page, long pageSize) {
        pagination(page, pageSize);
        long total = jdbc.sql("SELECT COUNT(*) FROM appointment WHERE user_id=:userId")
                .param("userId", userId).query(Long.class).single();
        List<AppointmentResponse> records = jdbc.sql("""
                SELECT ap.id,ap.room_id,ap.contact_name,ap.contact_phone,ap.appointment_time,ap.status,
                       ap.created_at,r.room_number,a.name apartment_name
                FROM appointment ap JOIN room r ON ap.room_id=r.id JOIN apartment a ON r.apartment_id=a.id
                WHERE ap.user_id=:userId ORDER BY ap.appointment_time DESC,ap.id DESC LIMIT :limit OFFSET :offset
                """).param("userId", userId).param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> new AppointmentResponse(rs.getLong("id"), rs.getLong("room_id"),
                        rs.getString("contact_name"), rs.getString("contact_phone"),
                        rs.getObject("appointment_time", LocalDateTime.class).atOffset(ZoneOffset.UTC),
                        rs.getString("status"), rs.getObject("created_at", LocalDateTime.class).atOffset(ZoneOffset.UTC),
                        rs.getString("room_number"), rs.getString("apartment_name"))).list();
        return new AppointmentPageResponse(records, total, page, pageSize);
    }

    @Transactional
    public void cancel(Long userId, Long appointmentId) {
        if (appointmentId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预约ID必须大于0");
        Cancellation record = jdbc.sql("SELECT id,slot_id,status,appointment_time FROM appointment WHERE id=:id AND user_id=:userId FOR UPDATE")
                .param("id", appointmentId).param("userId", userId).query((rs, row) -> new Cancellation(
                        rs.getLong("id"), rs.getObject("slot_id", Long.class), rs.getString("status"),
                        rs.getObject("appointment_time", LocalDateTime.class))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "预约不存在"));
        if ("CANCELLED".equals(record.status())) return;
        if (!("PENDING".equals(record.status()) || "CONFIRMED".equals(record.status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前预约状态不能取消");
        }
        if (!record.time().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "预约已经开始，不能取消");
        }
        int changed = jdbc.sql("UPDATE appointment SET status='CANCELLED' WHERE id=:id AND user_id=:userId AND status IN ('PENDING','CONFIRMED')")
                .param("id", appointmentId).param("userId", userId).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "预约状态已变化，请刷新");
        if (record.slotId() != null && jdbc.sql("UPDATE appointment_slot SET reserved_count=reserved_count-1 WHERE id=:id AND reserved_count>0")
                .param("id", record.slotId()).update() != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "预约名额释放失败");
        }
    }

    private void pagination(long page, long pageSize) {
        if (page < 1 || page > 1_000_000 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
    }
    private record Cancellation(Long id, Long slotId, String status, LocalDateTime time) {}
}
