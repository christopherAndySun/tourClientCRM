package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.ThirdPartyDownloadResponse;
import com.tourcrm.service.ThirdPartyDownloadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/third-party-downloads")
public class ThirdPartyDownloadController {

    private final ThirdPartyDownloadService thirdPartyDownloadService;

    public ThirdPartyDownloadController(ThirdPartyDownloadService thirdPartyDownloadService) {
        this.thirdPartyDownloadService = thirdPartyDownloadService;
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<ThirdPartyDownloadResponse>> pending(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contactInfo,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String addMethod,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) String assignedSales,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(thirdPartyDownloadService.page(false, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/downloaded")
    public ApiResponse<PageResponse<ThirdPartyDownloadResponse>> downloaded(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contactInfo,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String addMethod,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) String assignedSales,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(thirdPartyDownloadService.page(true, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, page, pageSize, token));
    }

    @PostMapping("/{customerCode}/mark-downloaded")
    public ApiResponse<Boolean> markDownloaded(
            @PathVariable String customerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(thirdPartyDownloadService.markDownloaded(customerCode, token));
    }
}
