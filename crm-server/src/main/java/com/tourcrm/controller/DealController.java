package com.tourcrm.controller;

import com.alibaba.excel.EasyExcel;
import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.DealCancelRequest;
import com.tourcrm.dto.DealExportRow;
import com.tourcrm.dto.DealResponse;
import com.tourcrm.dto.DealSaveRequest;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.service.DealService;
import com.tourcrm.service.SystemAuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("/api/deals")
public class DealController {

    private final DealService dealService;
    private final SystemAuditService systemAuditService;

    public DealController(DealService dealService, SystemAuditService systemAuditService) {
        this.dealService = dealService;
        this.systemAuditService = systemAuditService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DealResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dealCode,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String salesEmployeeCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(dealService.listPage(keyword, dealCode, customerCode, customerName, status, startDate, endDate, salesEmployeeCode, page, pageSize, token));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dealCode,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String salesEmployeeCode,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletResponse response
    ) throws IOException {
        String fileName = URLEncoder.encode("成交记录-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        List<DealExportRow> rows = dealService.listForExport(keyword, dealCode, customerCode, customerName, status, startDate, endDate, salesEmployeeCode, token).stream()
                .map(DealExportRow::new)
                .toList();
        systemAuditService.record(token, "DEAL_EXPORT", "导出成交记录", "DEAL_EXPORT", "", "导出成交记录 " + rows.size() + " 条");
        EasyExcel.write(response.getOutputStream(), DealExportRow.class).sheet("成交记录").doWrite(rows);
    }

    @GetMapping("/{dealCode}")
    public ApiResponse<DealResponse> detail(
            @PathVariable String dealCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return dealService.findByCode(dealCode, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("成交记录不存在"));
    }

    @PostMapping
    public ApiResponse<DealResponse> create(
            @RequestBody DealSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(dealService.create(request, token));
    }

    @PutMapping("/{dealCode}")
    public ApiResponse<DealResponse> update(
            @PathVariable String dealCode,
            @RequestBody DealSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return dealService.update(dealCode, request, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("成交记录不存在"));
    }

    @PostMapping("/{dealCode}/cancel")
    public ApiResponse<Boolean> cancelDeal(
            @PathVariable String dealCode,
            @RequestBody(required = false) DealCancelRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(dealService.cancel(
                dealCode,
                request == null ? "" : request.remark(),
                request == null ? "" : request.refundAmount(),
                request == null ? "" : request.refundedAt(),
                token
        ));
    }
}
