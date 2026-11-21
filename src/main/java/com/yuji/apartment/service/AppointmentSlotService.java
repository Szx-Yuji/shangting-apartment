package com.yuji.apartment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuji.apartment.dto.AppointmentSlotCreateRequest;
import com.yuji.apartment.entity.AppointmentSlot;
import com.yuji.apartment.entity.Room;
import com.yuji.apartment.mapper.AppointmentSlotMapper;
import com.yuji.apartment.mapper.RoomMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuji.apartment.dto.AppointmentSlotPageResponse;
import com.yuji.apartment.dto.AppointmentSlotResponse;
import com.yuji.apartment.dto.AdminAppointmentSlotResponse;
import com.yuji.apartment.dto.AdminAppointmentSlotPageResponse;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AppointmentSlotService {

    private final RoomMapper roomMapper;
    private final AppointmentSlotMapper slotMapper;

    public AppointmentSlotService(
            RoomMapper roomMapper,
            AppointmentSlotMapper slotMapper
    ) {
        this.roomMapper = roomMapper;
        this.slotMapper = slotMapper;
    }

    @Transactional
    public Long create(AppointmentSlotCreateRequest request) {
        LocalDateTime startTime = request.startTime()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MICROS);

        LocalDateTime endTime = request.endTime()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MICROS);

        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "结束时间必须晚于开始时间"
            );
        }

        Room room = roomMapper.selectOne(
                new LambdaQueryWrapper<Room>()
                        .eq(Room::getId, request.roomId())
                        .last("FOR UPDATE")
        );

        if (room == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在"
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        if (!startTime.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开始时间必须晚于当前时间"
            );
        }

        AppointmentSlot conflict = slotMapper.selectOne(
                new LambdaQueryWrapper<AppointmentSlot>()
                        .eq(AppointmentSlot::getRoomId, room.getId())
                        .lt(AppointmentSlot::getStartTime, endTime)
                        .gt(AppointmentSlot::getEndTime, startTime)
                        .last("LIMIT 1 FOR UPDATE")
        );

        if (conflict != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该房间已存在重叠的预约时段"
            );
        }

        AppointmentSlot slot = new AppointmentSlot();
        slot.setRoomId(room.getId());
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setCapacity(request.capacity());
        slot.setReservedCount(0);
        slot.setCreatedAt(now);

        int rows = slotMapper.insert(slot);

        if (rows != 1 || slot.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "预约时段创建失败"
            );
        }

        return slot.getId();
    }

    @Transactional(readOnly = true)
    public AppointmentSlotPageResponse findAvailablePage(
            Long roomId,
            long page,
            long pageSize
    ) {
        if (roomId == null || roomId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page必须大于等于1，pageSize必须在1到100之间"
            );
        }

        Room room = roomMapper.selectById(roomId);

        if (room == null || !Boolean.TRUE.equals(room.getPublished())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在或未上架"
            );
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        LambdaQueryWrapper<AppointmentSlot> query =
                new LambdaQueryWrapper<AppointmentSlot>()
                        .eq(AppointmentSlot::getRoomId, roomId)
                        .gt(AppointmentSlot::getStartTime, now)
                        .apply("reserved_count < capacity")
                        .orderByAsc(AppointmentSlot::getStartTime)
                        .orderByAsc(AppointmentSlot::getId);

        Page<AppointmentSlot> result = slotMapper.selectPage(
                new Page<AppointmentSlot>(page, pageSize),
                query
        );

        List<AppointmentSlotResponse> records = new ArrayList<>();

        for (AppointmentSlot slot : result.getRecords()) {
            records.add(new AppointmentSlotResponse(
                    slot.getId(),
                    slot.getRoomId(),
                    slot.getStartTime().atOffset(ZoneOffset.UTC),
                    slot.getEndTime().atOffset(ZoneOffset.UTC),
                    slot.getCapacity() - slot.getReservedCount()
            ));
        }

        return new AppointmentSlotPageResponse(
                records,
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public AdminAppointmentSlotPageResponse findAdminPage(
            Long roomId,
            long page,
            long pageSize
    ) {
        // 1. 检查查询参数。
        if (roomId == null || roomId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "房间ID必须大于0"
            );
        }

        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page必须大于等于1，pageSize必须在1到100之间"
            );
        }

        // 2. 管理员可以查看未上架房间，只检查房间是否存在。
        Room room = roomMapper.selectById(roomId);

        if (room == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在"
            );
        }

        // 3. 构建查询条件。
        LambdaQueryWrapper<AppointmentSlot> query =
                new LambdaQueryWrapper<AppointmentSlot>()
                        .eq(AppointmentSlot::getRoomId, roomId)
                        .orderByAsc(AppointmentSlot::getStartTime)
                        .orderByAsc(AppointmentSlot::getId);

        // 4. 执行数据库分页查询。
        Page<AppointmentSlot> result = slotMapper.selectPage(
                new Page<AppointmentSlot>(page, pageSize),
                query
        );

        // 5. 将数据库实体转换为管理员响应 DTO。
        List<AdminAppointmentSlotResponse> records =
                new ArrayList<>();

        for (AppointmentSlot slot : result.getRecords()) {
            records.add(new AdminAppointmentSlotResponse(
                    slot.getId(),
                    slot.getRoomId(),
                    slot.getStartTime().atOffset(ZoneOffset.UTC),
                    slot.getEndTime().atOffset(ZoneOffset.UTC),
                    slot.getCapacity(),
                    slot.getReservedCount(),
                    slot.getCapacity() - slot.getReservedCount()
            ));
        }

        // 6. 将本页记录与分页信息一起返回。
        return new AdminAppointmentSlotPageResponse(
                records,
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }
}