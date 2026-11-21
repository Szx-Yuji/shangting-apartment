package com.yuji.apartment.dto;

public record UserAccountResponse(Long id, String username, String displayName, String role) {
    public UserAccountResponse(Long id, String username, String displayName) {
        this(id, username, displayName, "USER");
    }
}
