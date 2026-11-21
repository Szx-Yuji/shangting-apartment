package com.yuji.apartment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuji.apartment.entity.AppointmentRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppointmentRequestMapper
        extends BaseMapper<AppointmentRequest> {
}