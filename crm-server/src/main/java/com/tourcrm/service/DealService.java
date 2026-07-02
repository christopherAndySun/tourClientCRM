package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.DealResponse;
import com.tourcrm.dto.DealSaveRequest;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.UserSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DealService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final AuthService authService;
    private final CustomerClueService customerClueService;
    private final DatabaseStore databaseStore;

    public DealService(
            AuthService authService,
            CustomerClueService customerClueService,
            DatabaseStore databaseStore
    ) {
        this.authService = authService;
        this.customerClueService = customerClueService;
        this.databaseStore = databaseStore;
    }

    public PageResponse<DealResponse> listPage(String keyword, String dealCode, String customerCode, String customerName, String status, String startDate, String endDate, String salesEmployeeCode, Integer page, Integer pageSize, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        String scopedSales = "ADMIN".equals(currentUser.role()) ? salesEmployeeCode : currentUser.employeeCode();
        return databaseStore.queryDealReportPage(keyword, dealCode, customerCode, customerName, normalizeOptionalStatus(status), startDate, endDate, scopedSales, page, pageSize);
    }

    public List<DealResponse> listForExport(String keyword, String dealCode, String customerCode, String customerName, String status, String startDate, String endDate, String salesEmployeeCode, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        String scopedSales = "ADMIN".equals(currentUser.role()) ? salesEmployeeCode : currentUser.employeeCode();
        return databaseStore.queryDealReportForExport(keyword, dealCode, customerCode, customerName, normalizeOptionalStatus(status), startDate, endDate, scopedSales, 50000);
    }

    public Optional<DealResponse> findByCode(String dealCode, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        return databaseStore.findDealByCode(dealCode)
                .map(this::normalizeDeal)
                .map(this::syncWithClueStatus)
                .filter(item -> canView(item, currentUser));
    }

    @Transactional
    public DealResponse create(DealSaveRequest request, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        validate(request);
        if (databaseStore.dealExistsForCustomer(request.customerCode())) {
            throw new BusinessException("该客户已登记成交，请不要重复登记");
        }

        String now = nowText();
        String dealDate = StringUtils.hasText(request.dealDate()) ? request.dealDate().trim() : LocalDate.now().toString();
        int sequence = (int) databaseStore.countDeals() + 1;
        for (int attempt = 0; attempt < 30; attempt++) {
            DealResponse deal = new DealResponse(
                    createDealCode(sequence + attempt),
                    request.customerCode().trim(),
                    request.customerName().trim(),
                    request.deposit().trim(),
                    clean(request.bookingDate()),
                    clean(request.addWechatDate()),
                    clean(request.quoteText()),
                    clean(request.travelDate()),
                    clean(request.itinerary()),
                    dealDate,
                    currentUser.name(),
                    currentUser.employeeCode(),
                    sequence + attempt,
                    (int) databaseStore.countDealsByUser(currentUser.employeeCode()) + 1,
                    "DEPOSIT_PAID",
                    "",
                    "",
                    "",
                    "",
                    "",
                    now,
                    now
            );
            if (databaseStore.insertDeal(deal)) {
                customerClueService.markDealed(deal.customerCode());
                return deal;
            }
        }
        throw new BusinessException("成交编号生成冲突，请稍后重试");
    }

    @Transactional
    public Optional<DealResponse> update(String dealCode, DealSaveRequest request, String token) {
        requireDealPermission(token);
        validate(request);
        UserSession currentUser = authService.currentUser(token);
        Optional<DealResponse> oldOptional = databaseStore.findDealByCode(dealCode).map(this::normalizeDeal);
        if (oldOptional.isPresent() && canView(oldOptional.get(), currentUser)) {
            DealResponse old = oldOptional.get();
            DealResponse updated = new DealResponse(
                    old.dealCode(),
                    old.customerCode(),
                    request.customerName().trim(),
                    request.deposit().trim(),
                    clean(request.bookingDate()),
                    clean(request.addWechatDate()),
                    clean(request.quoteText()),
                    clean(request.travelDate()),
                    clean(request.itinerary()),
                    StringUtils.hasText(request.dealDate()) ? request.dealDate().trim() : old.dealDate(),
                    old.dealUser(),
                    old.dealUserCode(),
                    old.totalDealSequence(),
                    old.personalDealSequence(),
                    normalizeDealStatus(old.status()),
                    clean(old.refundAmount()),
                    clean(old.refundRemark()),
                    clean(old.refundedAt()),
                    clean(old.landingAt()),
                    clean(old.landingRemark()),
                    old.createdAt(),
                    nowText()
            );
            databaseStore.writeDeal(updated);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    @Transactional
    public boolean cancel(String dealCode, String remark, String refundAmount, String refundedAt, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        Optional<DealResponse> oldOptional = databaseStore.findDealByCode(dealCode).map(this::normalizeDeal);
        if (oldOptional.isPresent() && canView(oldOptional.get(), currentUser)) {
            DealResponse old = oldOptional.get();
            if ("REFUNDED".equals(normalizeDealStatus(old.status()))) {
                return true;
            }
            String now = nowText();
            DealResponse refunded = new DealResponse(
                    old.dealCode(),
                    old.customerCode(),
                    old.customerName(),
                    old.deposit(),
                    old.bookingDate(),
                    old.addWechatDate(),
                    old.quoteText(),
                    old.travelDate(),
                    old.itinerary(),
                    old.dealDate(),
                    old.dealUser(),
                    old.dealUserCode(),
                    old.totalDealSequence(),
                    old.personalDealSequence(),
                    "REFUNDED",
                    clean(refundAmount),
                    clean(remark),
                    StringUtils.hasText(refundedAt) ? refundedAt.trim() : now,
                    old.landingAt(),
                    old.landingRemark(),
                    old.createdAt(),
                    now
            );
            databaseStore.writeDeal(refunded);
            customerClueService.markRefunded(old.customerCode(), StringUtils.hasText(remark) ? remark : "成交记录退单", refundAmount, refunded.refundedAt());
            return true;
        }
        return false;
    }

    private void requireDealPermission(String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_DEALS)) {
            throw new BusinessException("没有成交记录权限，请联系管理员开通");
        }
    }

    private boolean canView(DealResponse item, UserSession currentUser) {
        if ("ADMIN".equals(currentUser.role())) {
            return true;
        }
        return currentUser.employeeCode().equals(item.dealUserCode());
    }

    private void validate(DealSaveRequest request) {
        if (!StringUtils.hasText(request.customerCode())) {
            throw new BusinessException("缺少客户编号");
        }
        if (!StringUtils.hasText(request.customerName())) {
            throw new BusinessException("请填写客户姓名");
        }
        if (!StringUtils.hasText(request.deposit())) {
            throw new BusinessException("请填写预付定金");
        }
    }

    private String createDealCode(int sequence) {
        String prefix = "D" + LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd"));
        return prefix + String.format("%03d", sequence);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMAT);
    }

    private DealResponse normalizeDeal(DealResponse old) {
        return new DealResponse(
                old.dealCode(),
                old.customerCode(),
                old.customerName(),
                old.deposit(),
                old.bookingDate(),
                old.addWechatDate(),
                old.quoteText(),
                old.travelDate(),
                old.itinerary(),
                old.dealDate(),
                old.dealUser(),
                old.dealUserCode(),
                old.totalDealSequence(),
                old.personalDealSequence(),
                normalizeDealStatus(old.status()),
                clean(old.refundAmount()),
                clean(old.refundRemark()),
                clean(old.refundedAt()),
                clean(old.landingAt()),
                clean(old.landingRemark()),
                old.createdAt(),
                old.updatedAt()
        );
    }

    private DealResponse syncWithClueStatus(DealResponse old) {
        Optional<com.tourcrm.dto.ClueResponse> clue = customerClueService.findByCustomerCodeForSystem(old.customerCode());
        if (clue.isEmpty()) {
            return old;
        }
        String clueStatus = clue.get().status();
        if (!"DEPOSIT_PAID".equals(clueStatus) && !"REFUNDED".equals(clueStatus) && !"LANDED".equals(clueStatus)) {
            return old;
        }
        String deposit = StringUtils.hasText(clue.get().depositAmount()) ? clue.get().depositAmount() : old.deposit();
        String refundAmount = "REFUNDED".equals(clueStatus) && StringUtils.hasText(clue.get().refundAmount()) ? clue.get().refundAmount() : old.refundAmount();
        String refundRemark = "REFUNDED".equals(clueStatus) && StringUtils.hasText(clue.get().statusRemark()) ? clue.get().statusRemark() : old.refundRemark();
        String refundedAt = "REFUNDED".equals(clueStatus) && StringUtils.hasText(clue.get().refundedAt()) ? clue.get().refundedAt() : old.refundedAt();
        String landingAt = "LANDED".equals(clueStatus) && StringUtils.hasText(clue.get().landingAt()) ? clue.get().landingAt() : old.landingAt();
        String landingRemark = "LANDED".equals(clueStatus) && StringUtils.hasText(clue.get().landingRemark()) ? clue.get().landingRemark() : old.landingRemark();
        return new DealResponse(
                old.dealCode(),
                old.customerCode(),
                old.customerName(),
                deposit,
                old.bookingDate(),
                old.addWechatDate(),
                old.quoteText(),
                old.travelDate(),
                old.itinerary(),
                old.dealDate(),
                old.dealUser(),
                old.dealUserCode(),
                old.totalDealSequence(),
                old.personalDealSequence(),
                clueStatus,
                clean(refundAmount),
                clean(refundRemark),
                clean(refundedAt),
                clean(landingAt),
                clean(landingRemark),
                old.createdAt(),
                old.updatedAt()
        );
    }

    private String normalizeDealStatus(String status) {
        String normalized = clean(status).toUpperCase(Locale.ROOT);
        if ("REFUNDED".equals(normalized)) {
            return "REFUNDED";
        }
        if ("LANDED".equals(normalized)) {
            return "LANDED";
        }
        return "DEPOSIT_PAID";
    }

    private String normalizeOptionalStatus(String status) {
        return StringUtils.hasText(status) ? normalizeDealStatus(status) : "";
    }

    private String dealStatusText(String status) {
        String normalized = normalizeDealStatus(status);
        if ("REFUNDED".equals(normalized)) {
            return "退单";
        }
        if ("LANDED".equals(normalized)) {
            return "已落地";
        }
        if (StringUtils.hasText(normalized)) {
            return "已交定金";
        }
        return switch (normalizeDealStatus(status)) {
            case "REFUNDED" -> "退单";
            case "LANDED" -> "已落地";
            default -> "已交定金";
        };
    }
}
