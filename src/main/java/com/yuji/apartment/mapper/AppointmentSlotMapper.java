package com.yuji.apartment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuji.apartment.entity.AppointmentSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentSlotMapper
        extends BaseMapper<AppointmentSlot> {

    @Update("""
            UPDATE appointment_slot
            SET reserved_count = reserved_count + 1
            WHERE id = #{slotId}
              AND reserved_count < capacity
            """)
    int reserveOne(@Param("slotId") Long slotId);

    @Update("""
        UPDATE appointment_slot
        SET reserved_count = reserved_count - 1
        WHERE id = #{slotId}
          AND reserved_count > 0
        """)
    int releaseOne(@Param("slotId") Long slotId);
}