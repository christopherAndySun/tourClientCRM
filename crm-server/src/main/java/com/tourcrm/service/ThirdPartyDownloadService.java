package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.ThirdPartyDownloadResponse;
import com.tourcrm.dto.UserSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ThirdPartyDownloadService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthService authService;
    private final DatabaseStore databaseStore;
    private final RealtimeEventService realtimeEventService;

    public ThirdPartyDownloadService(AuthService authService, DatabaseStore databaseStore, RealtimeEventService realtimeEventService) {
        this.authService = authService;
        this.databaseStore = databaseStore;
        this.realtimeEventService = realtimeEventService;
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
        ensurePermission(token);
        return databaseStore.queryThirdPartyDownloadPage(downloaded, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, page, pageSize);
    }

    public boolean markDownloaded(String customerCode, String token) {
        ensurePermission(token);
        UserSession user = authService.currentUser(token);
        boolean marked = databaseStore.markThirdPartyDownloaded(
                normalizeCustomerCode(customerCode),
                user.name(),
                user.employeeCode(),
                LocalDateTime.now().format(DATE_TIME_FORMAT)
        );
        if (!marked) {
            throw new BusinessException("客户线索不存在或已删除");
        }
        realtimeEventService.publish(
                "THIRD_PARTY_CHANGED",
                normalizeCustomerCode(customerCode),
                "三方下载池已更新",
                "客资已移入已下载列表",
                java.util.List.of(RealtimeEventService.TARGET_THIRD_PARTY_POOL)
        );
        return true;
    }

    private void ensurePermission(String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_THIRD_PARTY_POOL)) {
            throw new BusinessException("没有三方下载池权限，请联系管理员开通");
        }
    }

    private String normalizeCustomerCode(String customerCode) {
        if (customerCode == null || customerCode.trim().isEmpty()) {
            throw new BusinessException("客户编号不能为空");
        }
        return customerCode.trim().toUpperCase();
    }
}
