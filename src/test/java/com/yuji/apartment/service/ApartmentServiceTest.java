package com.yuji.apartment.service;

import com.yuji.apartment.cache.ApartmentListCache;
import com.yuji.apartment.mapper.ApartmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApartmentServiceTest {

    @Test
    void shouldRejectDeletingMissingApartment() {
        ApartmentMapper apartmentMapper = mock(ApartmentMapper.class);
        ApartmentService apartmentService =
                new ApartmentService(
                        apartmentMapper,
                        mock(ApartmentListCache.class)
                );

        when(apartmentMapper.deleteById(999L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> apartmentService.delete(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("公寓不存在: 999", exception.getReason());
    }

    @Test
    void shouldRejectDeletingApartmentWithRooms() {
        ApartmentMapper apartmentMapper = mock(ApartmentMapper.class);
        ApartmentService apartmentService =
                new ApartmentService(
                        apartmentMapper,
                        mock(ApartmentListCache.class)
                );

        when(apartmentMapper.deleteById(101L))
                .thenThrow(new DataIntegrityViolationException(
                        "模拟外键约束冲突"
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> apartmentService.delete(101L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(
                "该公寓下仍有房间，请先处理房间",
                exception.getReason()
        );
    }
}