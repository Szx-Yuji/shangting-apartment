package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentCreateResponse;
import com.yuji.apartment.dto.AppointmentSlotBookingRequest;
import com.yuji.apartment.entity.AppointmentRequest;
import com.yuji.apartment.mapper.AppointmentRequestMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentOwnershipTest {
    @Test void idempotencyHashIncludesTheAuthenticatedOwner() {
        AppointmentRequestMapper mapper = mock(AppointmentRequestMapper.class);
        AppointmentRequestService writer = mock(AppointmentRequestService.class);
        AppointmentBookingService service = new AppointmentBookingService(mapper, writer, new JsonMapper());
        String key = "d1958952-9a76-4dd7-8f8d-f0a00bddb30f";
        var request = new AppointmentSlotBookingRequest(2L, "测试租客", "13800000000");
        var hashes = new java.util.ArrayList<String>();
        when(writer.bookOnce(eq(key), anyString(), eq(request), anyLong())).thenAnswer(invocation -> {
            hashes.add(invocation.getArgument(1));
            return new AppointmentCreateResponse(100L, "PENDING");
        });
        service.book(key, request, 7L);
        service.book(key, request, 8L);
        assertNotEquals(hashes.get(0), hashes.get(1));

        when(writer.bookOnce(eq(key), anyString(), eq(request), eq(8L)))
                .thenThrow(new DuplicateKeyException("synthetic"));
        AppointmentRequest record = new AppointmentRequest();
        record.setAppointmentId(100L);
        record.setRequestHash(hashes.get(0));
        when(mapper.selectById(key)).thenReturn(record);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.book(key, request, 8L));
        assertEquals(409, exception.getStatusCode().value());
        verify(mapper, times(1)).selectById(key);
    }
}
