package com.yuji.apartment.controller;

import com.yuji.apartment.dto.FavoriteListingResponse;
import com.yuji.apartment.dto.UserAccountResponse;
import com.yuji.apartment.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {
    private final FavoriteService favorites;
    public FavoriteController(FavoriteService favorites) { this.favorites = favorites; }
    @GetMapping public List<FavoriteListingResponse> list(@AuthenticationPrincipal UserAccountResponse user) {
        return favorites.list(user.id());
    }
    @PutMapping("/{roomId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal UserAccountResponse user, @PathVariable Long roomId) { favorites.add(user.id(), roomId); }
    @DeleteMapping("/{roomId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal UserAccountResponse user, @PathVariable Long roomId) { favorites.remove(user.id(), roomId); }
}
