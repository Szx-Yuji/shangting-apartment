package com.yuji.apartment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuji.apartment.dto.ApartmentResponse;
import com.yuji.apartment.entity.Apartment;
import com.yuji.apartment.mapper.ApartmentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.yuji.apartment.dto.ApartmentCreateRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuji.apartment.dto.ApartmentPageResponse;
import com.yuji.apartment.dto.ApartmentUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import com.yuji.apartment.cache.ApartmentListCache;
import com.yuji.apartment.monitor.TimedOperation;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApartmentService {

    private final ApartmentMapper apartmentMapper;
    private final ApartmentListCache apartmentListCache;

    public ApartmentService(
            ApartmentMapper apartmentMapper,
            ApartmentListCache apartmentListCache
    ) {
        this.apartmentMapper = apartmentMapper;
        this.apartmentListCache = apartmentListCache;
    }

    public ApartmentResponse findById(Long id) {
        Apartment apartment = apartmentMapper.selectById(id);

        if (apartment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + id
            );
        }

        return toResponse(apartment);
    }

    public ApartmentPageResponse findPage(
            String city,
            long page,
            long pageSize
    ) {
        QueryWrapper<Apartment> query = new QueryWrapper<>();

        if (city != null && !city.isBlank()) {
            query.eq("city", city.strip());
        }

        query.orderByAsc("id");

        Page<Apartment> result = apartmentMapper.selectPage(
                new Page<Apartment>(page, pageSize),
                query
        );

        List<ApartmentResponse> responses = new ArrayList<>();

        for (Apartment apartment : result.getRecords()) {
            responses.add(toResponse(apartment));
        }

        return new ApartmentPageResponse(
                responses,
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    private ApartmentResponse toResponse(Apartment apartment) {
        return new ApartmentResponse(
                apartment.getId(),
                apartment.getName(),
                apartment.getCity(),
                apartment.getMonthlyRent()
        );
    }

    public ApartmentResponse create(ApartmentCreateRequest request) {
        Apartment apartment = new Apartment();

        apartment.setName(request.name());
        apartment.setCity(request.city());
        apartment.setMonthlyRent(request.monthlyRent());

        apartmentMapper.insert(apartment);

        apartmentListCache.evictAllAfterCommit();

        return toResponse(apartment);
    }

    public ApartmentResponse update(
            Long id,
            ApartmentUpdateRequest request
    ) {
        Apartment apartment = new Apartment();

        apartment.setId(id);
        apartment.setName(request.name());
        apartment.setCity(request.city());
        apartment.setMonthlyRent(request.monthlyRent());

        int updated = apartmentMapper.updateById(apartment);

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + id
            );
        }

        apartmentListCache.evictAllAfterCommit();

        return toResponse(apartment);
    }

    public void delete(Long id) {
        int deleted;

        try {
            deleted = apartmentMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该公寓下仍有房间，请先处理房间",
                    exception
            );
        }

        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "公寓不存在: " + id
            );
        }

        apartmentListCache.evictAllAfterCommit();
    }
}