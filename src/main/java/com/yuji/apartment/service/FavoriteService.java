package com.yuji.apartment.service;

import com.yuji.apartment.dto.FavoriteListingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Service
public class FavoriteService {
    private final JdbcClient jdbc;
    public FavoriteService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<FavoriteListingResponse> list(Long userId) {
        return jdbc.sql("""
                SELECT r.*,a.name apartment_name,a.city,a.address,
                       NOT EXISTS(SELECT 1 FROM lease_contract l WHERE l.room_id=r.id AND l.status='ACTIVE') available
                FROM favorite f JOIN room r ON f.room_id=r.id JOIN apartment a ON r.apartment_id=a.id
                WHERE f.user_id=:userId AND r.published=true ORDER BY f.created_at DESC,r.id DESC
                """).param("userId", userId).query((rs, row) -> new FavoriteListingResponse(
                        rs.getLong("id"), rs.getLong("apartment_id"), rs.getString("apartment_name"),
                        rs.getString("city"), rs.getString("address"), rs.getString("room_number"),
                        rs.getString("title"), rs.getInt("monthly_rent"), rs.getBigDecimal("area"),
                        rs.getInt("bedrooms"), rs.getString("orientation"), rs.getString("image_url"),
                        tags(rs.getString("tags")), rs.getBoolean("published"), rs.getBoolean("available"))).list();
    }

    @Transactional
    public void add(Long userId, Long roomId) {
        if (roomId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "房间ID必须大于0");
        if (jdbc.sql("SELECT COUNT(*) FROM room WHERE id=:id AND published=true").param("id", roomId).query(Long.class).single() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在或未上架");
        }
        jdbc.sql("INSERT INTO favorite(user_id,room_id,created_at) VALUES(:userId,:roomId,:now) ON DUPLICATE KEY UPDATE created_at=created_at")
                .param("userId", userId).param("roomId", roomId).param("now", LocalDateTime.now(ZoneOffset.UTC)).update();
    }

    public void remove(Long userId, Long roomId) {
        jdbc.sql("DELETE FROM favorite WHERE user_id=:userId AND room_id=:roomId")
                .param("userId", userId).param("roomId", roomId).update();
    }

    private List<String> tags(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::strip).filter(tag -> !tag.isEmpty()).toList();
    }
}
