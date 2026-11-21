package com.yuji.apartment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuji.apartment.dto.AppointmentCreateResponse;
import com.yuji.apartment.entity.Appointment;
import com.yuji.apartment.entity.Room;
import com.yuji.apartment.mapper.AppointmentMapper;
import com.yuji.apartment.mapper.RoomMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuji.apartment.dto.AppointmentPageResponse;
import com.yuji.apartment.dto.AppointmentResponse;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuji.apartment.dto.AppointmentStatusUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuji.apartment.dto.AppointmentListRow;
import com.yuji.apartment.dto.AppointmentSlotBookingRequest;
import com.yuji.apartment.entity.AppointmentSlot;
import com.yuji.apartment.mapper.AppointmentSlotMapper;
import com.yuji.apartment.monitor.TimedOperation;
import com.yuji.apartment.dto.AppointmentQueryRequest;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final RoomMapper roomMapper;
    private final AppointmentSlotMapper slotMapper;

    public AppointmentService(
            AppointmentMapper appointmentMapper,
            RoomMapper roomMapper,
            AppointmentSlotMapper slotMapper
    ) {
        this.appointmentMapper = appointmentMapper;
        this.roomMapper = roomMapper;
        this.slotMapper = slotMapper;
    }

    @TimedOperation
    @Transactional
    public AppointmentCreateResponse book(
            AppointmentSlotBookingRequest request
    ) {
        return book(request, null);
    }

    @TimedOperation
    @Transactional
    public AppointmentCreateResponse book(AppointmentSlotBookingRequest request, Long userId) {
        // 1. 第一次查询只用于确定时段属于哪个房间。
        AppointmentSlot selectedSlot =
                slotMapper.selectById(request.slotId());

        if (selectedSlot == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "预约时段不存在或不可预约"
            );
        }

        // 2. 先锁定房间，并检查是否上架。
        Room room = roomMapper.selectOne(
                new LambdaQueryWrapper<Room>()
                        .eq(Room::getId, selectedSlot.getRoomId())
                        .last("FOR SHARE")
        );

        if (room == null
                || !Boolean.TRUE.equals(room.getPublished())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "房间不存在或未上架"
            );
        }

        if (roomMapper.selectActiveLeaseIdForShare(room.getId()) != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该房间已有生效租约，暂不接受新的看房预约"
            );
        }

        // 3. 再锁定并重新读取时段，确认仍属于这个房间。
        AppointmentSlot slot = slotMapper.selectOne(
                new LambdaQueryWrapper<AppointmentSlot>()
                        .eq(AppointmentSlot::getId, request.slotId())
                        .eq(AppointmentSlot::getRoomId, room.getId())
                        .last("FOR UPDATE")
        );

        if (slot == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "预约时段不存在或不可预约"
            );
        }

        // 4. 拿到锁后，再检查时段是否已经开始。
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        if (!slot.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该时段已开始，无法预约"
            );
        }

        // 5. 调用上一节写的方法，尝试占用一个名额。
        int reserved = slotMapper.reserveOne(slot.getId());

        if (reserved != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该时段名额已满，请选择其他时段"
            );
        }

        // 6. 根据后端查到的房间和时段，构建预约记录。
        Appointment appointment = new Appointment();
        appointment.setRoomId(room.getId());
        appointment.setSlotId(slot.getId());
        appointment.setUserId(userId);
        appointment.setContactName(request.contactName().strip());
        appointment.setContactPhone(request.contactPhone());
        appointment.setAppointmentTime(slot.getStartTime());
        appointment.setStatus("PENDING");
        appointment.setCreatedAt(now);

        int rows = appointmentMapper.insert(appointment);

        if (rows != 1 || appointment.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "预约提交失败"
            );
        }

        return new AppointmentCreateResponse(
                appointment.getId(),
                appointment.getStatus()
        );
    }

    @TimedOperation
    @Transactional(readOnly = true)
    public AppointmentPageResponse findPage(
            AppointmentQueryRequest request
    ) {
        if (request.startTime() != null
                && request.endTime() != null
                && !request.startTime().isBefore(request.endTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开始时间必须早于结束时间"
            );
        }

        LocalDateTime startTime = null;

        if (request.startTime() != null) {
            startTime = request.startTime()
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        }

        LocalDateTime endTime = null;

        if (request.endTime() != null) {
            endTime = request.endTime()
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        }

        IPage<AppointmentListRow> result =
                appointmentMapper.selectListPage(
                        new Page<AppointmentListRow>(
                                request.page(),
                                request.pageSize()
                        ),
                        request.status(),
                        request.roomId(),
                        startTime,
                        endTime
                );

        List<AppointmentResponse> records =
                new ArrayList<>();

        for (AppointmentListRow row : result.getRecords()) {
            records.add(new AppointmentResponse(
                    row.id(),
                    row.roomId(),
                    row.contactName(),
                    row.contactPhone(),
                    row.appointmentTime()
                            .atOffset(ZoneOffset.UTC),
                    row.status(),
                    row.createdAt()
                            .atOffset(ZoneOffset.UTC),
                    row.roomNumber(),
                    row.apartmentName()
            ));
        }

        return new AppointmentPageResponse(
                records,
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Transactional
    public void updateStatus(
            Long id,
            AppointmentStatusUpdateRequest request
    ) {
        if (id == null || id < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预约ID必须大于0"
            );
        }

        String status = request.status();

        if (!"CONFIRMED".equals(status)
                && !"REJECTED".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预约状态只支持CONFIRMED或REJECTED"
            );
        }

        int updated = appointmentMapper.update(
                new LambdaUpdateWrapper<Appointment>()
                        .eq(Appointment::getId, id)
                        .eq(Appointment::getStatus, "PENDING")
                        .set(Appointment::getStatus, status)
        );

        if (updated == 0) {
            if (appointmentMapper.selectById(id) == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "预约不存在"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该预约已处理，请刷新后查看"
            );
        }

        // 只有本次成功拒绝预约，才继续释放名额。
        if ("REJECTED".equals(status)) {
            Appointment appointment =
                    appointmentMapper.selectById(id);

            if (appointment == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "预约处理失败，请稍后重试"
                );
            }

            // 历史预约可能没有关联时段，不需要释放名额。
            Long slotId = appointment.getSlotId();

            if (slotId != null) {
                int released = slotMapper.releaseOne(slotId);

                if (released != 1) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "预约处理失败，请稍后重试"
                    );
                }
            }
        }
    }
}
