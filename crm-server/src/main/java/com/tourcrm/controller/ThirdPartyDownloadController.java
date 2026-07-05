package com.tourcrm.controller;

import com.alibaba.excel.EasyExcel;
import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.ThirdPartyDownloadFailureExportRow;
import com.tourcrm.dto.ThirdPartyDownloadFailureRequest;
import com.tourcrm.dto.ThirdPartyDownloadFailureRow;
import com.tourcrm.dto.ThirdPartyDownloadResponse;
import com.tourcrm.service.SystemAuditService;
import com.tourcrm.service.ThirdPartyDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/third-party-downloads")
public class ThirdPartyDownloadController {

    private final ThirdPartyDownloadService thirdPartyDownloadService;
    private final SystemAuditService systemAuditService;

    public ThirdPartyDownloadController(ThirdPartyDownloadService thirdPartyDownloadService, SystemAuditService systemAuditService) {
        this.thirdPartyDownloadService = thirdPartyDownloadService;
        this.systemAuditService = systemAuditService;
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

    @PostMapping("/{customerCode}/restore-pending")
    public ApiResponse<Boolean> restorePending(
            @PathVariable String customerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(thirdPartyDownloadService.restorePending(customerCode, token));
    }

    @PostMapping("/{customerCode}/record-failure")
    public ApiResponse<Boolean> recordFailure(
            @PathVariable String customerCode,
            @RequestBody ThirdPartyDownloadFailureRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(thirdPartyDownloadService.recordFailure(customerCode, request == null ? "" : request.message(), token));
    }

    @GetMapping("/failures")
    public ApiResponse<PageResponse<ThirdPartyDownloadFailureRow>> failures(
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(thirdPartyDownloadService.failurePage(customerCode, operator, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/failures/export")
    public void exportFailures(
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletResponse response
    ) throws IOException {
        String fileName = URLEncoder.encode("三方下载失败明细-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        List<ThirdPartyDownloadFailureExportRow> rows = thirdPartyDownloadService.failuresForExport(customerCode, operator, startDate, endDate, token).stream()
                .map(ThirdPartyDownloadFailureExportRow::new)
                .toList();
        systemAuditService.record(token, "THIRD_PARTY_FAILURE_EXPORT", "导出三方下载失败明细", "THIRD_PARTY_DOWNLOAD", "", "导出三方下载失败明细 " + rows.size() + " 条");
        EasyExcel.write(response.getOutputStream(), ThirdPartyDownloadFailureExportRow.class).sheet("失败明细").doWrite(rows);
    }
}
