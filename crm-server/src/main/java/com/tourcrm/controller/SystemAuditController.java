package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.SystemAuditLogRow;
import com.tourcrm.service.SystemAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-audit")
public class SystemAuditController {

    private final SystemAuditService systemAuditService;

    public SystemAuditController(SystemAuditService systemAuditService) {
        this.systemAuditService = systemAuditService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SystemAuditLogRow>> page(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(systemAuditService.page(action, operator, targetType, targetCode, startDate, endDate, page, pageSize, token));
    }
}
