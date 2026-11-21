package com.yuji.apartment.dto;

public record SessionResponse(boolean authenticated, String role, Account account) {
    public record Account(Long id, String username, String displayName) {}
    public static SessionResponse anonymous() { return new SessionResponse(false, null, null); }
}
