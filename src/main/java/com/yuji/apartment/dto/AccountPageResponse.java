package com.yuji.apartment.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AccountPageResponse(List<Account> records, long total, long page, long pageSize) {
    public record Account(Long id, String username, String displayName, boolean enabled, OffsetDateTime createdAt) {}
}
