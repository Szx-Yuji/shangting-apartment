package com.yuji.apartment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuji.apartment.dto.RoomRentStatsResponse;
import com.yuji.apartment.entity.Room;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.yuji.apartment.dto.RoomDetailResponse;

@Mapper
public interface RoomMapper extends BaseMapper<Room> {

    /** Current read under the room lock; avoids an earlier RR snapshot hiding an activated lease. */
    @Select("SELECT id FROM lease_contract WHERE room_id = #{roomId} AND status = 'ACTIVE' LIMIT 1 FOR SHARE")
    Long selectActiveLeaseIdForShare(@Param("roomId") Long roomId);

    @Select("""
            SELECT COUNT(*) AS roomCount,
                   MIN(monthly_rent) AS minRent,
                   MAX(monthly_rent) AS maxRent
            FROM room
            WHERE apartment_id = #{apartmentId}
            """)
    RoomRentStatsResponse selectRentStats(
            @Param("apartmentId") Long apartmentId
    );

    @Select("""
        SELECT r.id AS id,
               r.apartment_id AS apartmentId,
               a.name AS apartmentName,
               a.city AS city,
               r.room_number AS roomNumber,
               r.monthly_rent AS monthlyRent,
               r.published AS published
        FROM room r
        JOIN apartment a ON r.apartment_id = a.id
        WHERE r.id = #{id}
        """)
    RoomDetailResponse selectDetailById(@Param("id") Long id);
}
