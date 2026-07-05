package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.ThirdPartyDownloadFailureRow;
import com.tourcrm.dto.ThirdPartyDownloadResponse;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ThirdPartyDownloadService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthService authService;
    private final DatabaseStore databaseStore;
    private final RealtimeEventService realtimeEventService;
    private final SystemAuditService systemAuditService;

    public ThirdPartyDownloadService(AuthService authService, DatabaseStore databaseStore, RealtimeEventService realtimeEventService, SystemAuditService systemAuditService) {
        this.authService = authService;
        this.databaseStore = databaseStore;
        this.realtimeEventService = realtimeEventService;
        this.systemAuditService = systemAuditService;
    }

    public PageResponse<ThirdPartyDownloadResponse> page(
            boolean downloaded,
            String keyword,
            String customerCode,
            String contactInfo,
            String sourcePlatform,
            String addMethod,
            String status,
            String uploader,
            String assignedSales,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize,
            String token
    ) {
        UserSession user = ensurePermission(token);
        Scope scope = scopeFor(user, token);
        return databaseStore.queryThirdPartyDownloadPage(scope.visibleUploaderCodes(), scope.visibleSalesCodes(), downloaded, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, page, pageSize);
    }

    public boolean markDownloaded(String customerCode, String token) {
        UserSession user = ensurePermission(token);
        String normalizedCode = normalizeCustomerCode(customerCode);
        ensureCanOperate(normalizedCode, user, token);
        String now = LocalDateTime.now().format(DATE_TIME_FORMAT);
        boolean marked = databaseStore.markThirdPartyDownloaded(
                normalizedCode,
                user.name(),
                user.employeeCode(),
                now
        );
        if (!marked) {
            throw new BusinessException("客户线索不存在或已删除");
        }
        databaseStore.recordThirdPartyDownloadLog(normalizedCode, "DOWNLOAD_SUCCESS", "下载成功", user.name(), user.employeeCode(), "Word 文档已生成并移入已下载列表", now);
        systemAuditService.recordUser(user.name(), user.employeeCode(), "WORD_DOWNLOAD", "下载 Word", "CLUE", normalizedCode, "三方下载池下载 Word");
        realtimeEventService.publish(
                "THIRD_PARTY_CHANGED",
                normalizedCode,
                "三方下载池已更新",
                "客资已移入已下载列表",
                java.util.List.of(RealtimeEventService.TARGET_THIRD_PARTY_POOL)
        );
        return true;
    }

    public boolean restorePending(String customerCode, String token) {
        UserSession user = ensurePermission(token);
        String normalizedCode = normalizeCustomerCode(customerCode);
        ensureCanOperate(normalizedCode, user, token);
        boolean restored = databaseStore.restoreThirdPartyPending(normalizedCode);
        if (!restored) {
            throw new BusinessException("该客资不在已下载列表中");
        }
        String now = LocalDateTime.now().format(DATE_TIME_FORMAT);
        databaseStore.recordThirdPartyDownloadLog(normalizedCode, "RESTORE_PENDING", "放回公共池", user.name(), user.employeeCode(), "从已下载列表放回公共池", now);
        systemAuditService.recordUser(user.name(), user.employeeCode(), "THIRD_PARTY_RESTORE", "放回三方公共池", "CLUE", normalizedCode, "从已下载列表放回公共池");
        realtimeEventService.publish(
                "THIRD_PARTY_CHANGED",
                normalizedCode,
                "三方下载池已更新",
                "客资已放回公共池",
                java.util.List.of(RealtimeEventService.TARGET_THIRD_PARTY_POOL)
        );
        return true;
    }

    public boolean recordFailure(String customerCode, String message, String token) {
        UserSession user = ensurePermission(token);
        String normalizedCode = normalizeCustomerCode(customerCode);
        ensureCanOperate(normalizedCode, user, token);
        databaseStore.recordThirdPartyDownloadLog(
                normalizedCode,
                "DOWNLOAD_FAILED",
                "下载失败",
                user.name(),
                user.employeeCode(),
                cleanMessage(message),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        systemAuditService.recordUser(user.name(), user.employeeCode(), "WORD_DOWNLOAD_FAILED", "Word 下载失败", "CLUE", normalizedCode, cleanMessage(message));
        return true;
    }

    public PageResponse<ThirdPartyDownloadFailureRow> failurePage(
            String customerCode,
            String operator,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize,
            String token
    ) {
        UserSession user = ensurePermission(token);
        Scope scope = scopeFor(user, token);
        return databaseStore.queryThirdPartyFailurePage(scope.visibleUploaderCodes(), scope.visibleSalesCodes(), customerCode, operator, startDate, endDate, page, pageSize);
    }

    public List<ThirdPartyDownloadFailureRow> failuresForExport(
            String customerCode,
            String operator,
            String startDate,
            String endDate,
            String token
    ) {
        UserSession user = ensurePermission(token);
        Scope scope = scopeFor(user, token);
        return databaseStore.queryThirdPartyFailuresForExport(scope.visibleUploaderCodes(), scope.visibleSalesCodes(), customerCode, operator, startDate, endDate, 50000);
    }

    private UserSession ensurePermission(String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_THIRD_PARTY_POOL)) {
            throw new BusinessException("没有三方下载池权限，请联系管理员开通");
        }
        return authService.currentUser(token);
    }

    private void ensureCanOperate(String customerCode, UserSession user, String token) {
        ClueResponse clue = databaseStore.findClueByCustomerCode(customerCode)
                .orElseThrow(() -> new BusinessException("客户线索不存在或已删除"));
        Scope scope = scopeFor(user, token);
        if (scope.unrestricted()) {
            return;
        }
        if (scope.visibleUploaderCodes() != null && scope.visibleUploaderCodes().contains(clue.uploaderEmployeeCode())) {
            return;
        }
        if (scope.visibleSalesCodes() != null && scope.visibleSalesCodes().contains(clue.assignedSalesEmployeeCode())) {
            return;
        }
        throw new BusinessException("没有操作该客资的权限");
    }

    private Scope scopeFor(UserSession user, String token) {
        if ("ADMIN".equals(user.role())) {
            return new Scope(null, null, true);
        }
        List<String> visibleCodes = authService.usersVisibleTo(token).stream()
                .filter(item -> user.position().equals(item.position()))
                .map(UserRecord::employeeCode)
                .toList();
        if ("SALES".equals(user.position())) {
            return new Scope(null, visibleCodes, false);
        }
        return new Scope(visibleCodes, null, false);
    }

    private String normalizeCustomerCode(String customerCode) {
        if (customerCode == null || customerCode.trim().isEmpty()) {
            throw new BusinessException("客户编号不能为空");
        }
        return customerCode.trim().toUpperCase();
    }

    private String cleanMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "下载 Word 失败";
        }
        return message.trim().length() > 500 ? message.trim().substring(0, 500) : message.trim();
    }

    private record Scope(List<String> visibleUploaderCodes, List<String> visibleSalesCodes, boolean unrestricted) {
    }
}
