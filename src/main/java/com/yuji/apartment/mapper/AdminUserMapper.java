package com.yuji.apartment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuji.apartment.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    @Select("""
            SELECT id,
                   username,
                   password_hash AS passwordHash,
                   enabled
            FROM admin_user
            WHERE username = #{username}
            """)
    AdminUser selectByUsername(@Param("username") String username);
}