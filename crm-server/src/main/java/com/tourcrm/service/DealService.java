package com.tourcrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.DealResponse;
import com.tourcrm.dto.DealSaveRequest;
import com.tourcrm.dto.UserSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class DealService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final TypeReference<List<DealResponse>> DEAL_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private final AuthService authService;
    private final CustomerClueService customerClueService;

    public DealService(
            ObjectMapper objectMapper,
            AuthService authService,
            CustomerClueService customerClueService,
            @Value("${app.deal-data-file:data/deals.json}") String dataFile
    ) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.customerClueService = customerClueService;
        this.dataFile = Path.of(dataFile);
    }

    public synchronized List<DealResponse> list(String keyword, String startDate, String endDate, String salesEmployeeCode, String token) {
        return list(keyword, null, null, null, null, startDate, endDate, salesEmployeeCode, token);
    }

    public synchronized List<DealResponse> list(String keyword, String dealCode, String customerCode, String customerName, String status, String startDate, String endDate, String salesEmployeeCode, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedSales = clean(salesEmployeeCode).toUpperCase(Locale.ROOT);
        List<DealResponse> persistedRows = readAll().stream()
                .map(this::syncWithClueStatus)
                .filter(item -> canView(item, currentUser))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> matchesDealFilters(item, dealCode, customerCode, customerName, status))
                .filter(item -> !StringUtils.hasText(normalizedSales) || normalizedSales.equals(item.dealUserCode()))
                .filter(item -> matchesDateRange(item, startDate, endDate))
                .toList();
        Set<String> dealCustomerCodes = new HashSet<>();
        persistedRows.forEach(item -> dealCustomerCodes.add(item.customerCode()));
        List<DealResponse> syntheticRows = customerClueService.dealReportClues(startDate, endDate, salesEmployeeCode, token).stream()
                .filter(clue -> !dealCustomerCodes.contains(clue.customerCode()))
                .map(this::dealFromClue)
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> matchesDealFilters(item, dealCode, customerCode, customerName, status))
                .toList();
        List<DealResponse> result = new ArrayList<>();
        result.addAll(persistedRows);
        result.addAll(syntheticRows);
        return result.stream()
                .sorted(Comparator.comparing(this::sortTime).reversed())
                .toList();
    }

    public synchronized Optional<DealResponse> findByCode(String dealCode, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        return readAll().stream()
                .map(this::syncWithClueStatus)
                .filter(item -> item.dealCode().equals(dealCode))
                .filter(item -> canView(item, currentUser))
                .findFirst();
    }

    public synchronized DealResponse create(DealSaveRequest request, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        validate(request);
        List<DealResponse> rows = readAll();
        if (rows.stream().anyMatch(item -> item.customerCode().equals(request.customerCode()))) {
            throw new BusinessException("该客户已登记成交，请不要重复登记");
        }

        String now = nowText();
        String dealDate = StringUtils.hasText(request.dealDate()) ? request.dealDate().trim() : LocalDate.now().toString();
        DealResponse deal = new DealResponse(
                createDealCode(rows),
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
                rows.size() + 1,
                personalDealCount(rows, currentUser.employeeCode()) + 1,
                "DEPOSIT_PAID",
                "",
                "",
                "",
                "",
                "",
                now,
                now
        );
        rows.add(deal);
        writeAll(rows);
        customerClueService.markDealed(deal.customerCode());
        return deal;
    }

    public synchronized Optional<DealResponse> update(String dealCode, DealSaveRequest request, String token) {
        requireDealPermission(token);
        validate(request);
        UserSession currentUser = authService.currentUser(token);
        List<DealResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            DealResponse old = rows.get(i);
            if (!old.dealCode().equals(dealCode) || !canView(old, currentUser)) {
                continue;
            }
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
            rows.set(i, updated);
            writeAll(rows);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    public synchronized boolean cancel(String dealCode, String remark, String refundAmount, String refundedAt, String token) {
        requireDealPermission(token);
        UserSession currentUser = authService.currentUser(token);
        List<DealResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            DealResponse old = rows.get(i);
            if (!old.dealCode().equals(dealCode) || !canView(old, currentUser)) {
                continue;
            }
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
            rows.set(i, refunded);
            writeAll(rows);
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

    private boolean matchesKeyword(DealResponse item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return contains(item.customerCode(), keyword)
                || contains(item.dealCode(), keyword)
                || contains(item.customerName(), keyword)
                || contains(item.dealUser(), keyword)
                || contains(item.dealUserCode(), keyword)
                || contains(dealStatusText(item.status()), keyword)
                || contains(item.refundRemark(), keyword)
                || contains(item.quoteText(), keyword)
                || contains(item.itinerary(), keyword);
    }

    private boolean matchesDealFilters(DealResponse item, String dealCode, String customerCode, String customerName, String status) {
        if (StringUtils.hasText(dealCode) && !contains(item.dealCode(), dealCode.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (StringUtils.hasText(customerCode) && !contains(item.customerCode(), customerCode.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (StringUtils.hasText(customerName) && !contains(item.customerName(), customerName.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (StringUtils.hasText(status) && !normalizeDealStatus(status).equals(normalizeDealStatus(item.status()))) {
            return false;
        }
        return true;
    }

    private boolean matchesDateRange(DealResponse item, String startDate, String endDate) {
        LocalDate date = parseDate(item.dealDate()).orElse(LocalDate.now());
        LocalDate start = parseDate(startDate).orElse(null);
        LocalDate end = parseDate(endDate).orElse(LocalDate.now());
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return !date.isAfter(end);
    }

    private Optional<LocalDate> parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
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

    private String createDealCode(List<DealResponse> rows) {
        return "D" + LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd")) + String.format("%03d", rows.size() + 1);
    }

    private int personalDealCount(List<DealResponse> rows, String employeeCode) {
        return (int) rows.stream().filter(item -> employeeCode.equals(item.dealUserCode())).count();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private List<DealResponse> readAll() {
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dataFile.toFile(), DEAL_LIST_TYPE).stream()
                    .map(this::normalizeDeal)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (IOException error) {
            throw new IllegalStateException("读取成交数据失败", error);
        }
    }

    private void writeAll(List<DealResponse> rows) {
        try {
            Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile.toFile(), rows);
        } catch (IOException error) {
            throw new IllegalStateException("保存成交数据失败", error);
        }
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMAT);
    }

    private DealResponse dealFromClue(com.tourcrm.dto.ClueResponse clue) {
        String dealTime = StringUtils.hasText(clue.updatedAt()) ? clue.updatedAt() : clue.createdAt();
        String dealDate = StringUtils.hasText(dealTime) && dealTime.length() >= 10 ? dealTime.substring(0, 10) : LocalDate.now().toString();
        return new DealResponse(
                "AUTO-" + clue.customerCode(),
                clue.customerCode(),
                StringUtils.hasText(clue.contactInfo()) ? clue.contactInfo() : clue.customerCode(),
                clean(clue.depositAmount()),
                "",
                "",
                "",
                "",
                clean(clue.remark()),
                dealDate,
                clue.assignedSales(),
                clue.assignedSalesEmployeeCode(),
                0,
                0,
                clue.status(),
                clean(clue.refundAmount()),
                clean(clue.statusRemark()),
                clean(clue.refundedAt()),
                clean(clue.landingAt()),
                clean(clue.landingRemark()),
                dealTime,
                dealTime
        );
    }

    private String sortTime(DealResponse item) {
        if (StringUtils.hasText(item.updatedAt())) {
            return item.updatedAt();
        }
        if (StringUtils.hasText(item.createdAt())) {
            return item.createdAt();
        }
        return "";
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
        Optional<com.tourcrm.dto.ClueResponse> clue = customerClueService.findByCustomerCode(old.customerCode());
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

    private String dealStatusText(String status) {
        return switch (normalizeDealStatus(status)) {
            case "REFUNDED" -> "退单";
            case "LANDED" -> "已落地";
            default -> "已交定金";
        };
    }
}
