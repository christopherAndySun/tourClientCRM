package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.SystemSettingsRecord;
import com.tourcrm.service.SystemSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    public SystemSettingsController(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping
    public ApiResponse<SystemSettingsRecord> get(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(systemSettingsService.get(token));
    }

    @PutMapping
    public ApiResponse<SystemSettingsRecord> save(
            @RequestBody SystemSettingsRecord request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(systemSettingsService.save(request, token));
    }
}
