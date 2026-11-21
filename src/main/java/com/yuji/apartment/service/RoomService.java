package com.yuji.apartment.service;

import com.yuji.apartment.dto.RoomCreateRequest;
import com.yuji.apartment.dto.RoomResponse;
import com.yuji.apartment.entity.Room;
import com.yuji.apartment.mapper.ApartmentMapper;
import com.yuji.apartment.mapper.RoomMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuji.apartment.dto.RoomPageResponse;
import com.yuji.apartment.dto.RoomUpdateRequest;
import com.yuji.apartment.dto.RoomQueryRequest;
import com.yuji.apartment.dto.RoomCountResponse;
import com.yuji.apartment.dto.RoomRentStatsResponse;
import com.yuji.apartment.dto.RoomDetailResponse;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yuji.apartment.dto.RoomPublicationRequest;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoomService {

    private final RoomMapper roomMapper;
    private final ApartmentMapper apartmentMapper;

    public RoomService(
            RoomMapper roomMapper,
            ApartmentMapper apartmentMapper
    ) {
        this.roomMapper = roomMapper;
        this.apartmentMapper = apartmentMapper;
    }

    public RoomResponse create(RoomCreateRequest request) {
        if (apartmentMapper.selectById(request.apartmentId()) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + request.apartmentId()
            );
        }

        Room room = new Room();
        room.setApartmentId(request.apartmentId());
        room.setRoomNumber(request.roomNumber().strip());
        room.setMonthlyRent(request.monthlyRent());
        room.setPublished(false);

        try {
            roomMapper.insert(room);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该公寓内的房间号已存在",
                    exception
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "房间信息与当前数据状态冲突，请刷新后重试",
                    exception
            );
        }

        return new RoomResponse(
                room.getId(),
                room.getApartmentId(),
                room.getRoomNumber(),
                room.getMonthlyRent(),
                room.getPublished()
        );
    }

    public RoomPageResponse findPage(RoomQueryRequest request) {
        if (apartmentMapper.selectById(request.apartmentId()) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + request.apartmentId()
            );
        }

        QueryWrapper<Room> query = new QueryWrapper<>();

        query.eq("apartment_id", request.apartmentId());

        if (request.published() != null) {
            query.eq("published", request.published());
        }

        if (request.roomNumber() != null
                && !request.roomNumber().isBlank()) {
            query.like("room_number", request.roomNumber().strip());
        }

        if (request.minRent() != null) {
            query.ge("monthly_rent", request.minRent());
        }

        if (request.maxRent() != null) {
            query.le("monthly_rent", request.maxRent());
        }

        switch (request.sort()) {
            case "id" -> query.orderByAsc("id");

            case "rentAsc" -> query.orderByAsc("monthly_rent")
                    .orderByAsc("id");

            case "rentDesc" -> query.orderByDesc("monthly_rent")
                    .orderByAsc("id");

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sort只支持id、rentAsc、rentDesc"
            );
        }

        Page<Room> result = roomMapper.selectPage(
                new Page<Room>(request.page(), request.pageSize()),
                query
        );

        List<RoomResponse> records = new ArrayList<>();

        for (Room room : result.getRecords()) {
            records.add(new RoomResponse(
                    room.getId(),
                    room.getApartmentId(),
                    room.getRoomNumber(),
                    room.getMonthlyRent(),
                    room.getPublished()
            ));
        }

        return new RoomPageResponse(
                records,
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    public RoomPageResponse findPublishedPage(RoomQueryRequest request) {
        RoomQueryRequest publishedRequest = new RoomQueryRequest(
                request.apartmentId(),
                request.roomNumber(),
                request.minRent(),
                request.maxRent(),
                request.sort(),
                request.page(),
                request.pageSize(),
                true
        );

        return findPage(publishedRequest);
    }

    public RoomCountResponse countByApartmentId(Long apartmentId) {
        if (apartmentMapper.selectById(apartmentId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + apartmentId
            );
        }

        QueryWrapper<Room> query = new QueryWrapper<>();
        query.eq("apartment_id", apartmentId);

        long count = roomMapper.selectCount(query);

        return new RoomCountResponse(apartmentId, count);
    }

    public RoomRentStatsResponse getRentStats(Long apartmentId) {
        if (apartmentMapper.selectById(apartmentId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + apartmentId
            );
        }

        return roomMapper.selectRentStats(apartmentId);
    }

    public RoomDetailResponse findDetailById(Long id) {
        RoomDetailResponse detail = roomMapper.selectDetailById(id);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在: " + id
            );
        }

        return detail;
    }

    public RoomResponse findById(Long id) {
        Room room = roomMapper.selectById(id);

        if (room == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在: " + id
            );
        }

        return new RoomResponse(
                room.getId(),
                room.getApartmentId(),
                room.getRoomNumber(),
                room.getMonthlyRent(),
                room.getPublished()
        );
    }

    public RoomDetailResponse findPublishedDetailById(Long id) {
        RoomDetailResponse detail = roomMapper.selectDetailById(id);

        if (detail == null || !Boolean.TRUE.equals(detail.published())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在或未上架"
            );
        }

        return detail;
    }

    public RoomResponse update(
            Long id,
            RoomUpdateRequest request
    ) {
        Room room = roomMapper.selectById(id);

        if (room == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在: " + id
            );
        }

        room.setRoomNumber(request.roomNumber().strip());
        room.setMonthlyRent(request.monthlyRent());

        int updated;

        try {
            updated = roomMapper.update(
                    new UpdateWrapper<Room>()
                            .eq("id", id)
                            .set("room_number", room.getRoomNumber())
                            .set("monthly_rent", room.getMonthlyRent())
            );
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该公寓内的房间号已存在",
                    exception
            );
        }

        if (updated == 0 && roomMapper.selectById(id) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在: " + id
            );
        }

        return new RoomResponse(
                room.getId(),
                room.getApartmentId(),
                room.getRoomNumber(),
                room.getMonthlyRent(),
                room.getPublished()
        );
    }

    public void updatePublication(
            Long id,
            RoomPublicationRequest request
    ) {
        UpdateWrapper<Room> update = new UpdateWrapper<>();

        update.eq("id", id);
        update.set("published", request.published());

        int updated = roomMapper.update(update);

        if (updated == 0 && roomMapper.selectById(id) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在: " + id
            );
        }
    }

    public void delete(Long id) {
        int deleted;

        try {
            deleted = roomMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该房间存在关联记录，无法删除，请改为下架房间",
                    exception
            );
        }

        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在: " + id
            );
        }
    }
}