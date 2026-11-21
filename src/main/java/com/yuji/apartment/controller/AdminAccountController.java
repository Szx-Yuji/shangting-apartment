package com.yuji.apartment.controller;

import com.yuji.apartment.dto.*;
import com.yuji.apartment.service.AdminAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminAccountController {
    private final AdminAccountService accounts;
    public AdminAccountController(AdminAccountService accounts) { this.accounts = accounts; }
    @GetMapping("/users") public AccountPageResponse users(@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="10") long pageSize) { return accounts.users(page, pageSize); }
    @GetMapping("/accounts") public AccountPageResponse admins(@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="10") long pageSize) { return accounts.admins(page, pageSize); }
    @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED)
    public AccountPageResponse.Account create(@Valid @RequestBody AdminAccountCreateRequest body) { return accounts.create(body); }
    @PatchMapping("/accounts/{id}/enabled") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enabled(@AuthenticationPrincipal AdminAccountResponse admin, @PathVariable Long id,
            @Valid @RequestBody AccountEnabledRequest body) { accounts.setEnabled(admin.id(), id, body.enabled()); }
    @PatchMapping("/users/{id}/enabled") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void userEnabled(@PathVariable Long id, @Valid @RequestBody AccountEnabledRequest body) { accounts.setUserEnabled(id, body.enabled()); }
}
