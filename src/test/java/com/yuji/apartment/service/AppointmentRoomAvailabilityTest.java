package com.yuji.apartment.service;

import com.yuji.apartment.dto.AppointmentSlotBookingRequest;
import com.yuji.apartment.entity.AppointmentSlot;
import com.yuji.apartment.entity.Room;
import com.yuji.apartment.mapper.AppointmentMapper;
import com.yuji.apartment.mapper.AppointmentSlotMapper;
import com.yuji.apartment.mapper.RoomMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentRoomAvailabilityTest {
    @Test void leasedRoomCannotConsumeAnotherAppointmentSlot() {
        AppointmentMapper appointments = mock(AppointmentMapper.class);
        RoomMapper rooms = mock(RoomMapper.class);
        AppointmentSlotMapper slots = mock(AppointmentSlotMapper.class);
        AppointmentService service = new AppointmentService(appointments, rooms, slots);
        AppointmentSlot slot = new AppointmentSlot(); slot.setId(2L); slot.setRoomId(3L);
        Room room = new Room(); room.setId(3L); room.setPublished(true);
        when(slots.selectById(2L)).thenReturn(slot);
        when(rooms.selectOne(any())).thenReturn(room);
        when(rooms.selectActiveLeaseIdForShare(3L)).thenReturn(40L);

        var error = assertThrows(ResponseStatusException.class, () ->
                service.book(new AppointmentSlotBookingRequest(2L,"测试租客","13800000000"),7L));
        assertEquals(409,error.getStatusCode().value());
        verify(slots,never()).reserveOne(anyLong());
        verify(slots,never()).selectOne(any());
        verifyNoInteractions(appointments);
        var order = inOrder(rooms);
        order.verify(rooms).selectOne(any());
        order.verify(rooms).selectActiveLeaseIdForShare(3L);
    }
}
