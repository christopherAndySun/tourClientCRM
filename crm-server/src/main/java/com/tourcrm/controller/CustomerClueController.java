package com.tourcrm.controller;

import com.alibaba.excel.EasyExcel;
import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.ClueAssignRequest;
import com.tourcrm.dto.AssignLogReportRow;
import com.tourcrm.dto.ClueExportRow;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ClueSaveRequest;
import com.tourcrm.dto.ClueStatsResponse;
import com.tourcrm.dto.ClueStatusUpdateRequest;
import com.tourcrm.dto.CustomerHistoryResponse;
import com.tourcrm.dto.EmployeeCluesResponse;
import com.tourcrm.dto.OperationLogReportRow;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.PerformanceExportRow;
import com.tourcrm.dto.PerformanceRowResponse;
import com.tourcrm.service.CustomerClueService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/clues")
public class CustomerClueController {

    private final CustomerClueService customerClueService;

    public CustomerClueController(CustomerClueService customerClueService) {
        this.customerClueService = customerClueService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ClueResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contactInfo,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) String assignedSales,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.listPage(keyword, customerCode, contactInfo, sourcePlatform, status, uploader, assignedSales, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/sales-pool/public")
    public ApiResponse<PageResponse<ClueResponse>> publicSalesPool(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contactInfo,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedSales,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.publicSalesPoolPage(keyword, customerCode, contactInfo, sourcePlatform, status, assignedSales, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/sales-pool/mine")
    public ApiResponse<PageResponse<ClueResponse>> mySalesPool(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contactInfo,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedSales,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.mySalesPoolPage(keyword, customerCode, contactInfo, sourcePlatform, status, assignedSales, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/assign-logs")
    public ApiResponse<PageResponse<AssignLogReportRow>> assignLogs(
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String salesEmployeeCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.assignLogReportPage(customerCode, action, operator, salesEmployeeCode, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResponse<OperationLogReportRow>> operationLogs(
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.operationLogReportPage(customerCode, operator, field, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String contactInfo,
            @RequestParam(required = false) String sourcePlatform,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) String assignedSales,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletResponse response
    ) throws IOException {
        String fileName = URLEncoder.encode("客户线索-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        List<ClueExportRow> rows = customerClueService.listForExport(keyword, customerCode, contactInfo, sourcePlatform, status, uploader, assignedSales, startDate, endDate, token).stream()
                .map(ClueExportRow::new)
                .toList();
        EasyExcel.write(response.getOutputStream(), ClueExportRow.class).sheet("客户线索").doWrite(rows);
    }

    @GetMapping("/stats")
    public ApiResponse<ClueStatsResponse> stats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.stats(startDate, endDate, token));
    }

    @GetMapping("/stats/detail")
    public ApiResponse<PageResponse<ClueResponse>> statsDetail(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.statsDetailPage(startDate, endDate, type, value, page, pageSize, token));
    }

    @GetMapping("/performance")
    public ApiResponse<List<PerformanceRowResponse>> performance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.performance(startDate, endDate, token));
    }

    @GetMapping("/performance/export")
    public void exportPerformance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletResponse response
    ) throws IOException {
        String fileName = URLEncoder.encode("员工绩效-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        List<PerformanceExportRow> rows = customerClueService.performance(startDate, endDate, token).stream()
                .map(PerformanceExportRow::new)
                .toList();
        EasyExcel.write(response.getOutputStream(), PerformanceExportRow.class).sheet("员工绩效").doWrite(rows);
    }

    @GetMapping("/performance/{employeeCode}/clues")
    public ApiResponse<EmployeeCluesResponse> employeeClues(
            @PathVariable String employeeCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.employeeClues(employeeCode, startDate, endDate, page, pageSize, token));
    }

    @GetMapping("/{customerCode}")
    public ApiResponse<ClueResponse> detail(
            @PathVariable String customerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return customerClueService.findByCustomerCode(customerCode, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("客户线索不存在"));
    }

    @GetMapping("/{customerCode}/history")
    public ApiResponse<CustomerHistoryResponse> history(
            @PathVariable String customerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.customerHistory(customerCode, token));
    }

    @PostMapping
    public ApiResponse<ClueResponse> create(
            @RequestBody ClueSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.create(request, token));
    }

    @PutMapping("/{customerCode}")
    public ApiResponse<ClueResponse> update(
            @PathVariable String customerCode,
            @RequestBody ClueSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return customerClueService.update(customerCode, request, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("客户线索不存在"));
    }

    @PutMapping("/{customerCode}/assign")
    public ApiResponse<ClueResponse> assign(
            @PathVariable String customerCode,
            @RequestBody ClueAssignRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return customerClueService.assignSales(customerCode, request, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("客户线索不存在"));
    }

    @PutMapping("/{customerCode}/claim")
    public ApiResponse<ClueResponse> claim(
            @PathVariable String customerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return customerClueService.claimSalesClue(customerCode, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("客户线索不存在"));
    }

    @PutMapping("/{customerCode}/release")
    public ApiResponse<ClueResponse> release(
            @PathVariable String customerCode,
            @RequestBody ClueAssignRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return customerClueService.releaseSalesClue(customerCode, request, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("客户线索不存在"));
    }

    @PutMapping("/{customerCode}/status")
    public ApiResponse<ClueResponse> updateStatus(
            @PathVariable String customerCode,
            @RequestBody ClueStatusUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return customerClueService.updateStatus(customerCode, request, token)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("客户线索不存在"));
    }

    @DeleteMapping("/{customerCode}")
    public ApiResponse<Boolean> delete(
            @PathVariable String customerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.delete(customerCode, token));
    }
}
