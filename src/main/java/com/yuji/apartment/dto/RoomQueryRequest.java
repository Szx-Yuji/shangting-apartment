package com.yuji.apartment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record RoomQueryRequest(
        @NotNull(message = "所属公寓不能为空")
        @Positive(message = "公寓ID必须大于0")
        Long apartmentId,

        @Size(max = 20, message = "房间号查询条件不能超过20个字符")
        String roomNumber,

        @Positive(message = "最低租金必须大于0")
        Integer minRent,

        @Positive(message = "最高租金必须大于0")
        Integer maxRent,

        String sort,

        @Positive(message = "页码必须大于0")
        Long page,

        @Positive(message = "每页条数必须大于0")
        @Max(value = 100, message = "每页条数不能超过100")
        Long pageSize,

        Boolean published
) {
    public RoomQueryRequest {
        if (sort == null || sort.isEmpty()) {
            sort = "id";
        }

        if (page == null) {
            page = 1L;
        }

        if (pageSize == null) {
            pageSize = 10L;
        }
    }

    @AssertTrue(message = "最低租金不能大于最高租金")
    public boolean isRentRangeValid() {
        return minRent == null
                || maxRent == null
                || minRent <= maxRent;
    }
}