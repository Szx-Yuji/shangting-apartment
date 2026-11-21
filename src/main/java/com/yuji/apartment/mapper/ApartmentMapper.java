package com.yuji.apartment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuji.apartment.entity.Apartment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApartmentMapper extends BaseMapper<Apartment> {
}