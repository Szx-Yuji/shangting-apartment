package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentResponse;
import com.yuji.apartment.dto.AppointmentListRow;
import com.yuji.apartment.dto.AppointmentQueryRequest;
import com.yuji.apartment.mapper.AppointmentMapper;
import com.yuji.apartment.mapper.AppointmentSlotMapper;
import com.yuji.apartment.mapper.RoomMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.type.LocalDateTimeTypeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Models a UTC MySQL connection under a JVM in Asia/Shanghai. DATETIME is a wall-time value. */
class DatabaseTimeMappingTest {
    @Test void bookingSelectedAtTenInShanghaiReturnsTheSameInstantForPersonalList() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            LocalDateTime utc = LocalDateTime.of(2026, 11, 1, 2, 0);
            ResultSet rs = row(utc);
            JdbcClient jdbc = queryClient(rs);
            AppointmentResponse result = new MyAppointmentService(jdbc).findPage(7L,1,10).records().getFirst();
            assertEquals(utc.toInstant(ZoneOffset.UTC),result.appointmentTime().toInstant());
            assertEquals(LocalTime.of(10,0),result.appointmentTime().withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalTime());
            assertEquals(utc.toInstant(ZoneOffset.UTC),result.createdAt().toInstant());
            // The administrator path uses MyBatis' typed LocalDateTime handler; compare both API paths.
            LocalDateTime adminUtc = new LocalDateTimeTypeHandler().getNullableResult(rs,"appointment_time");
            AppointmentMapper mapper = mock(AppointmentMapper.class);
            Page<AppointmentListRow> page = new Page<>(1,10);
            page.setTotal(1);
            page.setRecords(List.of(new AppointmentListRow(1L,1L,"101","公寓","测试","13800000000",adminUtc,"PENDING",adminUtc)));
            when(mapper.selectListPage(any(),isNull(),isNull(),isNull(),isNull())).thenReturn(page);
            var admin = new AppointmentService(mapper,mock(RoomMapper.class),mock(AppointmentSlotMapper.class))
                    .findPage(new AppointmentQueryRequest(null,null,null,null,null,null)).records().getFirst();
            assertEquals(admin.appointmentTime(),result.appointmentTime());
            assertEquals(LocalTime.of(10,0),admin.appointmentTime().withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalTime());
            verify(rs,never()).getTimestamp(anyString());
        } finally { TimeZone.setDefault(original); }
    }

    @Test void accountCreatedAtDoesNotAcquireTheJvmOffset() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            LocalDateTime utc = LocalDateTime.of(2026, 11, 1, 2, 0);
            ResultSet rs = row(utc);
            var service = new AdminAccountService(queryClient(rs),mock(PasswordEncoder.class));
            var result = service.users(1,10).records().getFirst();
            assertEquals(utc.toInstant(ZoneOffset.UTC),result.createdAt().toInstant());
            verify(rs,never()).getTimestamp(anyString());
        } finally { TimeZone.setDefault(original); }
    }

    @Test void alreadyStartedAppointmentCannotBeCancelledDueToAnEightHourShift() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            ResultSet rs = row(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
            JdbcClient jdbc = queryClient(rs);
            var error = assertThrows(ResponseStatusException.class, () -> new MyAppointmentService(jdbc).cancel(7L,1L));
            assertEquals(409,error.getStatusCode().value());
            verify(rs,never()).getTimestamp(anyString());
            verify(jdbc,never()).sql(startsWith("UPDATE"));
        } finally { TimeZone.setDefault(original); }
    }

    private ResultSet row(LocalDateTime utc) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(anyString())).thenReturn(1L);
        when(rs.getString(anyString())).thenReturn("synthetic");
        when(rs.getString("status")).thenReturn("PENDING");
        when(rs.getBoolean("enabled")).thenReturn(true);
        when(rs.getObject("slot_id",Long.class)).thenReturn(1L);
        when(rs.getObject("appointment_time",LocalDateTime.class)).thenReturn(utc);
        when(rs.getObject("created_at",LocalDateTime.class)).thenReturn(utc);
        // A Timestamp represents an instant; toLocalDateTime would apply the JVM's +08 offset again.
        when(rs.getTimestamp(anyString())).thenReturn(Timestamp.from(utc.toInstant(ZoneOffset.UTC)));
        return rs;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private JdbcClient queryClient(ResultSet rs) throws SQLException {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec count = mock(JdbcClient.StatementSpec.class,RETURNS_SELF);
        JdbcClient.StatementSpec rows = mock(JdbcClient.StatementSpec.class,RETURNS_SELF);
        JdbcClient.MappedQuerySpec<Long> countResult = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(startsWith("SELECT COUNT"))).thenReturn(count);
        when(jdbc.sql(argThat(sql -> sql != null && !sql.startsWith("SELECT COUNT")))).thenReturn(rows);
        when(count.query(Long.class)).thenReturn(countResult);
        when(countResult.single()).thenReturn(1L);
        when(rows.query(any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(0);
            Object result = mapper.mapRow(rs,0);
            JdbcClient.MappedQuerySpec resultSet = mock(JdbcClient.MappedQuerySpec.class);
            when(resultSet.list()).thenReturn(List.of(result));
            when(resultSet.optional()).thenReturn(Optional.of(result));
            return resultSet;
        });
        return jdbc;
    }
}
