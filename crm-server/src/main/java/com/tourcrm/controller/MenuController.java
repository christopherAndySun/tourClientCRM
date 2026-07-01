package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.MenuItemRecord;
import com.tourcrm.service.MenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public ApiResponse<List<MenuItemRecord>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(menuService.list(token));
    }

    @PutMapping
    public ApiResponse<List<MenuItemRecord>> save(
            @RequestBody List<MenuItemRecord> request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(menuService.save(request, token));
    }
}
