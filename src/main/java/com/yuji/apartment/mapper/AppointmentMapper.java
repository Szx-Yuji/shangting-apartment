package com.yuji.apartment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuji.apartment.dto.AppointmentListRow;
import com.yuji.apartment.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {

    @Select("""
            <script>
            SELECT ap.id AS id,
                   ap.room_id AS roomId,
                   r.room_number AS roomNumber,
                   a.name AS apartmentName,
                   ap.contact_name AS contactName,
                   ap.contact_phone AS contactPhone,
                   ap.appointment_time AS appointmentTime,
                   ap.status AS status,
                   ap.created_at AS createdAt
            FROM appointment ap
            JOIN room r ON ap.room_id = r.id
            JOIN apartment a ON r.apartment_id = a.id
            <where>
                        <if test="status != null">
                            ap.status = #{status}
                        </if>
            
                        <if test="roomId != null">
                            AND ap.room_id = #{roomId}
                        </if>
            
                        <if test="startTime != null">
                            AND ap.appointment_time &gt;= #{startTime}
                        </if>
            
                        <if test="endTime != null">
                            AND ap.appointment_time &lt; #{endTime}
                        </if>
                    </where>
            ORDER BY ap.appointment_time ASC, ap.id ASC
            </script>
            """)
    IPage<AppointmentListRow> selectListPage(
            @Param("page") IPage<AppointmentListRow> page,
            @Param("status") String status,
            @Param("roomId") Long roomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}