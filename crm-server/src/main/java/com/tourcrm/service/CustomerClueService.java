package com.tourcrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.AssignLogRecord;
import com.tourcrm.dto.AssignLogReportRow;
import com.tourcrm.dto.ClueAssignRequest;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ClueSaveRequest;
import com.tourcrm.dto.ClueStatsResponse;
import com.tourcrm.dto.ClueStatusUpdateRequest;
import com.tourcrm.dto.EmployeeCluesResponse;
import com.tourcrm.dto.FollowRecord;
import com.tourcrm.dto.ImageFileDto;
import com.tourcrm.dto.CustomerHistoryResponse;
import com.tourcrm.dto.OperationLogRecord;
import com.tourcrm.dto.OperationLogReportRow;
import com.tourcrm.dto.PerformanceRowResponse;
import com.tourcrm.dto.StatusChangeRecord;
import com.tourcrm.dto.UserRecord;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerClueService {

    private static final DateTimeFormatter DATE_CODE_FORMAT = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> STATUSES = Set.of("NEW", "FOLLOWING", "INVALID", "DEPOSIT_PAID", "REFUNDED", "LANDED");
    private static final Set<String> SOURCE_PLATFORMS = Set.of("DOUYIN", "XIAOHONGSHU");
    private static final TypeReference<List<ClueResponse>> CLUE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private final AuthService authService;

    public CustomerClueService(
            ObjectMapper objectMapper,
            AuthService authService,
            @Value("${app.clue-data-file:data/clues.json}") String dataFile
    ) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.dataFile = Path.of(dataFile);
    }

    public synchronized List<ClueResponse> list(String keyword, String startDate, String endDate, String token) {
        return list(keyword, null, null, null, null, null, null, startDate, endDate, token);
    }

    public synchronized List<ClueResponse> list(String keyword, String customerCode, String contactInfo, String sourcePlatform, String status, String uploader, String assignedSales, String startDate, String endDate, String token) {
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return readAll().stream()
                .filter(item -> canViewInWorkspace(item, currentUser, usersByCode))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> matchesClueFilters(item, customerCode, contactInfo, sourcePlatform, status, uploader, assignedSales))
                .filter(item -> matchesDateRange(item, startDate, endDate))
                .sorted(Comparator.comparing(this::sortTime).reversed())
                .toList();
    }

    public synchronized List<ClueResponse> publicSalesPool(String keyword, String token) {
        return publicSalesPool(keyword, null, null, null, null, null, null, token);
    }

    public synchronized List<ClueResponse> publicSalesPool(String keyword, String customerCode, String contactInfo, String sourcePlatform, String assignedSales, String startDate, String endDate, String token) {
        ensureAssignPermission(token);
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return readAll().stream()
                .filter(item -> canViewInPublicSalesPool(item, currentUser, usersByCode))
                .filter(item -> !StringUtils.hasText(item.assignedSalesEmployeeCode()))
                .filter(item -> !"DEPOSIT_PAID".equals(item.status()) && !"REFUNDED".equals(item.status()) && !"LANDED".equals(item.status()))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> matchesClueFilters(item, customerCode, contactInfo, sourcePlatform, null, null, assignedSales))
                .filter(item -> matchesDateRange(item, startDate, endDate))
                .sorted(Comparator.comparing(this::sortTime).reversed())
                .toList();
    }

    public synchronized List<ClueResponse> mySalesPool(String keyword, String token) {
        return mySalesPool(keyword, null, null, null, null, null, null, token);
    }

    public synchronized List<ClueResponse> mySalesPool(String keyword, String customerCode, String contactInfo, String sourcePlatform, String assignedSales, String startDate, String endDate, String token) {
        UserSession currentUser = authService.currentUser(token);
        if (!"ADMIN".equals(currentUser.role()) && !"SALES".equals(currentUser.position())) {
            throw new BusinessException("只有销售或管理员可以查看销售池");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return readAll().stream()
                .filter(item -> "ADMIN".equals(currentUser.role()) || currentUser.employeeCode().equals(item.assignedSalesEmployeeCode()))
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> matchesClueFilters(item, customerCode, contactInfo, sourcePlatform, null, null, assignedSales))
                .filter(item -> matchesDateRange(item, startDate, endDate))
                .sorted(Comparator.comparing(this::sortTime).reversed())
                .toList();
    }

    public synchronized List<AssignLogReportRow> assignLogReport(String customerCode, String action, String operator, String salesEmployeeCode, String startDate, String endDate, String token) {
        ensureAssignPermission(token);
        String normalizedCustomerCode = clean(customerCode).toLowerCase(Locale.ROOT);
        String normalizedAction = clean(action).toUpperCase(Locale.ROOT);
        String normalizedOperator = clean(operator).toLowerCase(Locale.ROOT);
        String normalizedSales = clean(salesEmployeeCode).toUpperCase(Locale.ROOT);
        return readAll().stream()
                .flatMap(clue -> safeAssignLogs(clue).stream().map(log -> new AssignLogReportRow(
                        clue.customerCode(),
                        normalizeSourcePlatform(clue.sourcePlatform()),
                        clean(clue.contactInfo()),
                        clue.status(),
                        clue.uploader(),
                        clue.uploaderEmployeeCode(),
                        clue.assignedSales(),
                        clue.assignedSalesEmployeeCode(),
                        log.action(),
                        log.actionText(),
                        log.operator(),
                        log.operatorCode(),
                        log.fromSales(),
                        log.fromSalesEmployeeCode(),
                        log.toSales(),
                        log.toSalesEmployeeCode(),
                        log.remark(),
                        log.createdAt()
                )))
                .filter(row -> !StringUtils.hasText(normalizedCustomerCode) || contains(row.customerCode(), normalizedCustomerCode))
                .filter(row -> !StringUtils.hasText(normalizedAction) || normalizedAction.equals(clean(row.action()).toUpperCase(Locale.ROOT)))
                .filter(row -> !StringUtils.hasText(normalizedOperator) || contains(row.operator(), normalizedOperator) || contains(row.operatorCode(), normalizedOperator))
                .filter(row -> !StringUtils.hasText(normalizedSales) || normalizedSales.equals(clean(row.fromSalesCode()).toUpperCase(Locale.ROOT)) || normalizedSales.equals(clean(row.toSalesCode()).toUpperCase(Locale.ROOT)) || normalizedSales.equals(clean(row.currentSalesEmployeeCode()).toUpperCase(Locale.ROOT)))
                .filter(row -> matchesLogDateRange(row.createdAt(), startDate, endDate))
                .sorted(Comparator.comparing(AssignLogReportRow::createdAt).reversed())
                .toList();
    }

    public synchronized List<OperationLogReportRow> operationLogReport(String customerCode, String operator, String field, String startDate, String endDate, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_OPERATION_LOGS)) {
            throw new BusinessException("没有操作日志权限，请联系管理员开通");
        }
        String normalizedCustomerCode = clean(customerCode).toLowerCase(Locale.ROOT);
        String normalizedOperator = clean(operator).toLowerCase(Locale.ROOT);
        String normalizedField = clean(field).toLowerCase(Locale.ROOT);
        return readAll().stream()
                .flatMap(clue -> safeOperationLogs(clue).stream().map(log -> new OperationLogReportRow(
                        clue.customerCode(),
                        normalizeSourcePlatform(clue.sourcePlatform()),
                        clean(clue.contactInfo()),
                        clue.status(),
                        clue.uploader(),
                        clue.uploaderEmployeeCode(),
                        clue.assignedSales(),
                        clue.assignedSalesEmployeeCode(),
                        log.action(),
                        log.actionText(),
                        log.operator(),
                        log.operatorCode(),
                        log.field(),
                        log.fieldText(),
                        log.oldValue(),
                        log.newValue(),
                        log.createdAt()
                )))
                .filter(row -> !StringUtils.hasText(normalizedCustomerCode) || contains(row.customerCode(), normalizedCustomerCode))
                .filter(row -> !StringUtils.hasText(normalizedOperator) || contains(row.operator(), normalizedOperator) || contains(row.operatorCode(), normalizedOperator))
                .filter(row -> !StringUtils.hasText(normalizedField) || contains(row.field(), normalizedField) || contains(row.fieldText(), normalizedField))
                .filter(row -> matchesLogDateRange(row.createdAt(), startDate, endDate))
                .sorted(Comparator.comparing(OperationLogReportRow::createdAt).reversed())
                .toList();
    }

    public synchronized ClueStatsResponse stats(String startDate, String endDate, String token) {
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        Map<String, UserRecord> usersByCode = userMap(visibleUsers);
        List<ClueResponse> rows = visibleCluesForManagement(startDate, endDate, token, visibleUsers, usersByCode);
        Map<String, Long> statusCounts = rows.stream()
                .collect(Collectors.groupingBy(ClueResponse::status, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> uploaderCounts = rows.stream()
                .collect(Collectors.groupingBy(ClueResponse::uploader, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> salesCounts = rows.stream()
                .filter(item -> StringUtils.hasText(item.assignedSales()))
                .collect(Collectors.groupingBy(ClueResponse::assignedSales, LinkedHashMap::new, Collectors.counting()));
        long repeatDemandCount = rows.stream().filter(item -> Boolean.TRUE.equals(item.repeatDemand())).count();
        long todayCount = rows.stream().filter(item -> itemDate(item).map(LocalDate.now()::equals).orElse(false)).count();
        return new ClueStatsResponse(rows.size(), todayCount, repeatDemandCount, rows.size() - repeatDemandCount, statusCounts, uploaderCounts, salesCounts);
    }

    public synchronized List<ClueResponse> statsDetail(String startDate, String endDate, String type, String value, String token) {
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        Map<String, UserRecord> usersByCode = userMap(visibleUsers);
        String normalizedType = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "ALL";
        String normalizedValue = clean(value);
        return visibleCluesForManagement(startDate, endDate, token, visibleUsers, usersByCode).stream()
                .filter(item -> matchesStatsDetail(item, normalizedType, normalizedValue))
                .toList();
    }

    public synchronized List<ClueResponse> dealReportClues(String startDate, String endDate, String salesEmployeeCode, String token) {
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        Map<String, UserRecord> usersByCode = userMap(visibleUsers);
        String normalizedSales = clean(salesEmployeeCode).toUpperCase(Locale.ROOT);
        return readAll().stream()
                .filter(item -> canViewInManagement(item, authService.currentUser(token), visibleUsers, usersByCode))
                .filter(item -> "DEPOSIT_PAID".equals(item.status()) || "REFUNDED".equals(item.status()) || "LANDED".equals(item.status()))
                .filter(item -> matchesDealReportDateRange(item, startDate, endDate))
                .filter(item -> StringUtils.hasText(item.assignedSalesEmployeeCode()))
                .filter(item -> !StringUtils.hasText(normalizedSales) || normalizedSales.equals(item.assignedSalesEmployeeCode()))
                .toList();
    }

    public synchronized List<PerformanceRowResponse> performance(String startDate, String endDate, String token) {
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        Map<String, UserRecord> usersByCode = userMap(visibleUsers);
        List<ClueResponse> rows = visibleCluesForManagement(startDate, endDate, token, visibleUsers, usersByCode);
        return visibleUsers.stream()
                .sorted(Comparator.comparing(UserRecord::employeeCode))
                .map(user -> toPerformanceRow(user, cluesForEmployee(rows, user.employeeCode(), usersByCode)))
                .toList();
    }

    public synchronized EmployeeCluesResponse employeeClues(String employeeCode, String startDate, String endDate, String token) {
        String normalizedEmployeeCode = normalizeEmployeeCode(employeeCode);
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        Map<String, UserRecord> usersByCode = userMap(visibleUsers);
        UserRecord user = usersByCode.get(normalizedEmployeeCode);
        if (user == null) {
            throw new BusinessException("无权查看该员工数据");
        }
        List<ClueResponse> rows = visibleCluesForManagement(startDate, endDate, token, visibleUsers, usersByCode);
        List<ClueResponse> clues = cluesForEmployee(rows, normalizedEmployeeCode, usersByCode);
        return new EmployeeCluesResponse(toPerformanceRow(user, clues), clues);
    }

    public synchronized Optional<ClueResponse> findByCustomerCode(String customerCode) {
        return readAll().stream()
                .filter(item -> item.customerCode().equals(customerCode))
                .findFirst();
    }

    public synchronized CustomerHistoryResponse customerHistory(String customerCode, String token) {
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        List<ClueResponse> rows = readAll();
        ClueResponse current = rows.stream()
                .filter(item -> item.customerCode().equals(customerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("客户线索不存在"));
        String contactKey = normalizeContact(current.contactInfo());
        String rootCode = rootCustomerCode(current);
        List<ClueResponse> demands = rows.stream()
                .filter(item -> belongsToSameCustomer(item, contactKey, rootCode))
                .filter(item -> canViewInWorkspace(item, currentUser, usersByCode))
                .sorted(Comparator.comparing(item -> item.demandSequence() == null ? 1 : item.demandSequence()))
                .toList();
        String customerKey = StringUtils.hasText(contactKey) ? contactKey : rootCode;
        return new CustomerHistoryResponse(customerKey, demands.size(), demands);
    }

    public synchronized ClueResponse create(ClueSaveRequest request, String token) {
        UserSession currentUser = authService.currentUser(token);
        List<ClueResponse> rows = readAll();
        String normalizedContact = normalizeContact(request.contactInfo());
        List<ClueResponse> sameContactRows = StringUtils.hasText(normalizedContact)
                ? findSameContactRows(rows, normalizedContact)
                : List.of();
        if (!sameContactRows.isEmpty() && !Boolean.TRUE.equals(request.allowRepeatDemand())) {
            ClueResponse first = sameContactRows.get(0);
            throw new BusinessException("当前客户已经被" + first.uploader() + "联系过，请不要重复保存");
        }

        String status = normalizeStatus(request.status(), "NEW");
        String now = nowText();
        boolean repeatDemand = !sameContactRows.isEmpty();
        String originalCustomerCode = repeatDemand ? rootCustomerCode(sameContactRows.get(0)) : null;
        int demandSequence = repeatDemand ? sameContactRows.size() + 1 : 1;
        ClueResponse clue = new ClueResponse(
                createCustomerCode(rows, currentUser.employeeCode()),
                normalizeSourcePlatform(request.sourcePlatform()),
                clean(request.contactInfo()),
                hasWechatId(request.hasWechatId()),
                currentUser.name(),
                currentUser.employeeCode(),
                status,
                clean(request.remark()),
                safeImages(request.douyinImages()),
                safeImages(request.wechatImages()),
                repeatDemand,
                originalCustomerCode,
                demandSequence,
                null,
                null,
                clean(request.depositAmount()),
                clean(request.statusRemark()),
                clean(request.refundAmount()),
                clean(request.refundedAt()),
                clean(request.landingAt()),
                clean(request.landingRemark()),
                List.of(statusRecord(status, currentUser, clean(request.depositAmount()), clean(request.statusRemark()))),
                initialFollowRecords(request.remark(), currentUser, now),
                List.of(),
                List.of(),
                now,
                now
        );
        rows.add(clue);
        writeAll(rows);
        return clue;
    }

    public synchronized Optional<ClueResponse> update(String customerCode, ClueSaveRequest request, String token) {
        List<ClueResponse> rows = readAll();
        UserSession currentUser = authService.currentUser(token);
        ensureUniqueContactOnUpdate(rows, request.contactInfo(), customerCode);
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            String status = normalizeStatus(request.status(), old.status());
            ClueResponse updated = new ClueResponse(
                    old.customerCode(),
                    normalizeSourcePlatform(request.sourcePlatform(), old.sourcePlatform()),
                    clean(request.contactInfo()),
                    hasWechatId(request.hasWechatId()),
                    old.uploader(),
                    old.uploaderEmployeeCode(),
                    status,
                    StringUtils.hasText(request.remark()) ? clean(request.remark()) : old.remark(),
                    safeImages(request.douyinImages()),
                    safeImages(request.wechatImages()),
                    Boolean.TRUE.equals(old.repeatDemand()),
                    old.originalCustomerCode(),
                    old.demandSequence() == null ? 1 : old.demandSequence(),
                    old.assignedSales(),
                    old.assignedSalesEmployeeCode(),
                    clean(request.depositAmount()),
                    clean(request.statusRemark()),
                    clean(request.refundAmount()),
                    clean(request.refundedAt()),
                    clean(request.landingAt()),
                    clean(request.landingRemark()),
                    statusHistoryForEdit(old, status, clean(request.depositAmount()), clean(request.statusRemark())),
                    followRecordsForEdit(old, request.remark(), currentUser),
                    safeAssignLogs(old),
                    operationLogsForEdit(old, request, status, currentUser),
                    old.createdAt(),
                    nowText()
            );
            rows.set(i, updated);
            writeAll(rows);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    public synchronized Optional<ClueResponse> assignSales(String customerCode, ClueAssignRequest request, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_ASSIGN)) {
            throw new BusinessException("没有分配管理权限，请联系管理员开通");
        }
        UserSession currentUser = authService.currentUser(token);
        UserRecord sales = authService.listAllUsersForSystem().stream()
                .filter(user -> user.employeeCode().equals(normalizeEmployeeCode(request.salesEmployeeCode())))
                .filter(user -> "SALES".equals(user.position()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("销售员工不存在"));
        List<ClueResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            String action = StringUtils.hasText(old.assignedSalesEmployeeCode()) ? "TRANSFER" : "ASSIGN";
            String remark = ("TRANSFER".equals(action) ? "转派给" : "分配给") + sales.name() + appendRemark(request.remark());
            ClueResponse updated = copyWithStatus(
                    old,
                    "FOLLOWING",
                    sales.name(),
                    sales.employeeCode(),
                    old.depositAmount(),
                    remark,
                    appendStatusHistory(old, statusRecord("FOLLOWING", currentUser, old.depositAmount(), remark)),
                    appendAssignLog(old, assignLog(action, currentUser, old.assignedSales(), old.assignedSalesEmployeeCode(), sales.name(), sales.employeeCode(), request.remark()))
            );
            rows.set(i, updated);
            writeAll(rows);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    public synchronized Optional<ClueResponse> claimSalesClue(String customerCode, String token) {
        UserSession currentUser = authService.currentUser(token);
        if (!"SALES".equals(currentUser.position())) {
            throw new BusinessException("只有销售可以领取线索");
        }
        List<ClueResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            if (StringUtils.hasText(old.assignedSalesEmployeeCode())) {
                rows.set(i, copyWithAssignLogs(old, appendAssignLog(old, assignLog(
                        "CLAIM_CONFLICT",
                        currentUser,
                        old.assignedSales(),
                        old.assignedSalesEmployeeCode(),
                        currentUser.name(),
                        currentUser.employeeCode(),
                        "线索已被其他销售领取"
                ))));
                writeAll(rows);
                throw new BusinessException("线索已被其他销售领取，请刷新后查看");
            }
            String remark = "销售领取：" + currentUser.name();
            ClueResponse updated = copyWithStatus(
                    old,
                    "FOLLOWING",
                    currentUser.name(),
                    currentUser.employeeCode(),
                    old.depositAmount(),
                    remark,
                    appendStatusHistory(old, statusRecord("FOLLOWING", currentUser, old.depositAmount(), remark)),
                    appendAssignLog(old, assignLog("CLAIM", currentUser, old.assignedSales(), old.assignedSalesEmployeeCode(), currentUser.name(), currentUser.employeeCode(), "销售领取"))
            );
            rows.set(i, updated);
            writeAll(rows);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    public synchronized Optional<ClueResponse> releaseSalesClue(String customerCode, ClueAssignRequest request, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_ASSIGN)) {
            throw new BusinessException("没有分配管理权限，请联系管理员开通");
        }
        UserSession currentUser = authService.currentUser(token);
        List<ClueResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            if (!StringUtils.hasText(old.assignedSalesEmployeeCode())) {
                throw new BusinessException("当前线索还没有分配销售");
            }
            String remark = "释放销售" + appendRemark(request.remark());
            ClueResponse updated = copyWithStatus(
                    old,
                    "FOLLOWING",
                    null,
                    null,
                    old.depositAmount(),
                    remark,
                    appendStatusHistory(old, statusRecord("FOLLOWING", currentUser, old.depositAmount(), remark)),
                    appendAssignLog(old, assignLog("RELEASE", currentUser, old.assignedSales(), old.assignedSalesEmployeeCode(), null, null, request.remark()))
            );
            rows.set(i, updated);
            writeAll(rows);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    public synchronized Optional<ClueResponse> updateStatus(String customerCode, ClueStatusUpdateRequest request, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_CLUES) && !authService.hasMenuPermission(token, AuthService.MENU_DEALS)) {
            throw new BusinessException("没有客户状态更新权限，请联系管理员开通");
        }
        UserSession currentUser = authService.currentUser(token);
        String status = normalizeStatus(request.status(), "");
        List<ClueResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            String depositAmount = clean(request.depositAmount());
            String remark = clean(request.remark());
            String refundAmount = clean(request.refundAmount());
            String refundedAt = clean(request.refundedAt());
            String landingAt = clean(request.landingAt());
            String landingRemark = clean(request.landingRemark());
            List<OperationLogRecord> operationLogs = statusOperationLogs(old, currentUser, status, depositAmount, remark, refundAmount, refundedAt, landingAt, landingRemark);
            ClueResponse updated = copyWithStatusAndLogs(
                    old,
                    status,
                    old.assignedSales(),
                    old.assignedSalesEmployeeCode(),
                    depositAmount,
                    remark,
                    refundAmount,
                    refundedAt,
                    landingAt,
                    landingRemark,
                    appendStatusHistory(old, statusRecord(status, currentUser, depositAmount, remark)),
                    safeAssignLogs(old),
                    operationLogs
            );
            rows.set(i, updated);
            writeAll(rows);
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    public synchronized boolean delete(String customerCode) {
        List<ClueResponse> rows = readAll();
        boolean removed = rows.removeIf(item -> item.customerCode().equals(customerCode));
        if (removed) {
            writeAll(rows);
        }
        return removed;
    }

    synchronized void markDealed(String customerCode) {
        List<ClueResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            StatusChangeRecord record = new StatusChangeRecord("DEPOSIT_PAID", statusText("DEPOSIT_PAID"), "系统", "SYSTEM", old.depositAmount(), "成交记录同步为已交定金", nowText());
            List<OperationLogRecord> logs = statusOperationLogs(old, systemOperator(), "DEPOSIT_PAID", old.depositAmount(), old.statusRemark(), old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark());
            rows.set(i, copyWithStatusAndLogs(old, "DEPOSIT_PAID", old.assignedSales(), old.assignedSalesEmployeeCode(), old.depositAmount(), old.statusRemark(), old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark(), appendStatusHistory(old, record), logs));
            writeAll(rows);
            return;
        }
        throw new BusinessException("客户线索不存在");
    }

    synchronized void markRefunded(String customerCode, String remark, String refundAmount, String refundedAt) {
        List<ClueResponse> rows = readAll();
        for (int i = 0; i < rows.size(); i++) {
            ClueResponse old = rows.get(i);
            if (!old.customerCode().equals(customerCode)) {
                continue;
            }
            String finalRemark = clean(remark);
            String finalRefundAmount = clean(refundAmount);
            String finalRefundedAt = defaultTime(refundedAt);
            StatusChangeRecord record = new StatusChangeRecord("REFUNDED", statusText("REFUNDED"), "系统", "SYSTEM", old.depositAmount(), finalRemark, nowText());
            List<OperationLogRecord> logs = statusOperationLogs(old, systemOperator(), "REFUNDED", old.depositAmount(), finalRemark, finalRefundAmount, finalRefundedAt, old.landingAt(), old.landingRemark());
            rows.set(i, copyWithStatusAndLogs(old, "REFUNDED", old.assignedSales(), old.assignedSalesEmployeeCode(), old.depositAmount(), finalRemark, finalRefundAmount, finalRefundedAt, old.landingAt(), old.landingRemark(), appendStatusHistory(old, record), logs));
            writeAll(rows);
            return;
        }
    }

    private ClueResponse copyWithStatus(ClueResponse old, String status, String salesName, String salesCode, String depositAmount, String statusRemark, List<StatusChangeRecord> history) {
        return copyWithStatus(old, status, salesName, salesCode, depositAmount, statusRemark, old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark(), history, safeAssignLogs(old));
    }

    private ClueResponse copyWithStatus(ClueResponse old, String status, String salesName, String salesCode, String depositAmount, String statusRemark, String refundAmount, String refundedAt, String landingAt, String landingRemark, List<StatusChangeRecord> history) {
        return copyWithStatus(old, status, salesName, salesCode, depositAmount, statusRemark, refundAmount, refundedAt, landingAt, landingRemark, history, safeAssignLogs(old));
    }

    private ClueResponse copyWithStatus(ClueResponse old, String status, String salesName, String salesCode, String depositAmount, String statusRemark, List<StatusChangeRecord> history, List<AssignLogRecord> assignLogs) {
        return copyWithStatus(old, status, salesName, salesCode, depositAmount, statusRemark, old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark(), history, assignLogs);
    }

    private ClueResponse copyWithStatus(ClueResponse old, String status, String salesName, String salesCode, String depositAmount, String statusRemark, String refundAmount, String refundedAt, String landingAt, String landingRemark, List<StatusChangeRecord> history, List<AssignLogRecord> assignLogs) {
        return copyWithStatusAndLogs(old, status, salesName, salesCode, depositAmount, statusRemark, refundAmount, refundedAt, landingAt, landingRemark, history, assignLogs, safeOperationLogs(old));
    }

    private ClueResponse copyWithStatusAndLogs(ClueResponse old, String status, String salesName, String salesCode, String depositAmount, String statusRemark, String refundAmount, String refundedAt, String landingAt, String landingRemark, List<StatusChangeRecord> history, List<OperationLogRecord> operationLogs) {
        return copyWithStatusAndLogs(old, status, salesName, salesCode, depositAmount, statusRemark, refundAmount, refundedAt, landingAt, landingRemark, history, safeAssignLogs(old), operationLogs);
    }

    private ClueResponse copyWithStatusAndLogs(ClueResponse old, String status, String salesName, String salesCode, String depositAmount, String statusRemark, String refundAmount, String refundedAt, String landingAt, String landingRemark, List<StatusChangeRecord> history, List<AssignLogRecord> assignLogs, List<OperationLogRecord> operationLogs) {
        return new ClueResponse(
                old.customerCode(),
                normalizeSourcePlatform(old.sourcePlatform()),
                old.contactInfo(),
                hasWechatId(old.hasWechatId()),
                old.uploader(),
                old.uploaderEmployeeCode(),
                status,
                old.remark(),
                old.douyinImages(),
                old.wechatImages(),
                old.repeatDemand(),
                old.originalCustomerCode(),
                old.demandSequence(),
                salesName,
                salesCode,
                clean(depositAmount),
                clean(statusRemark),
                clean(refundAmount),
                clean(refundedAt),
                clean(landingAt),
                clean(landingRemark),
                history,
                safeFollowRecords(old),
                assignLogs,
                operationLogs,
                old.createdAt(),
                nowText()
        );
    }

    private ClueResponse copyWithAssignLogs(ClueResponse old, List<AssignLogRecord> assignLogs) {
        return new ClueResponse(
                old.customerCode(),
                normalizeSourcePlatform(old.sourcePlatform()),
                old.contactInfo(),
                hasWechatId(old.hasWechatId()),
                old.uploader(),
                old.uploaderEmployeeCode(),
                old.status(),
                old.remark(),
                old.douyinImages(),
                old.wechatImages(),
                old.repeatDemand(),
                old.originalCustomerCode(),
                old.demandSequence(),
                old.assignedSales(),
                old.assignedSalesEmployeeCode(),
                old.depositAmount(),
                old.statusRemark(),
                old.refundAmount(),
                old.refundedAt(),
                old.landingAt(),
                old.landingRemark(),
                safeHistory(old),
                safeFollowRecords(old),
                assignLogs,
                safeOperationLogs(old),
                old.createdAt(),
                nowText()
        );
    }

    private List<StatusChangeRecord> statusHistoryForEdit(ClueResponse old, String status, String depositAmount, String statusRemark) {
        boolean changed = !status.equals(old.status())
                || !clean(depositAmount).equals(clean(old.depositAmount()))
                || !clean(statusRemark).equals(clean(old.statusRemark()));
        if (!changed) {
            return safeHistory(old);
        }
        return appendStatusHistory(old, new StatusChangeRecord(status, statusText(status), "系统", "SYSTEM", depositAmount, statusRemark, nowText()));
    }

    private StatusChangeRecord statusRecord(String status, UserSession operator, String depositAmount, String remark) {
        return new StatusChangeRecord(status, statusText(status), operator.name(), operator.employeeCode(), clean(depositAmount), clean(remark), nowText());
    }

    private List<StatusChangeRecord> appendStatusHistory(ClueResponse old, StatusChangeRecord record) {
        List<StatusChangeRecord> history = new ArrayList<>(safeHistory(old));
        history.add(record);
        return history;
    }

    private List<StatusChangeRecord> safeHistory(ClueResponse clue) {
        if (clue.statusHistory() == null) {
            return new ArrayList<>();
        }
        return clue.statusHistory().stream()
                .map(this::normalizeStatusRecord)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private StatusChangeRecord normalizeStatusRecord(StatusChangeRecord record) {
        String status = normalizeStatus(record.status(), "NEW");
        return new StatusChangeRecord(
                status,
                statusText(status),
                record.operator(),
                record.operatorCode(),
                clean(record.depositAmount()),
                clean(record.remark()),
                record.createdAt()
        );
    }

    private List<FollowRecord> initialFollowRecords(String remark, UserSession operator, String createdAt) {
        if (!StringUtils.hasText(remark)) {
            return List.of();
        }
        return List.of(new FollowRecord(operator.name(), operator.employeeCode(), clean(remark), createdAt));
    }

    private List<FollowRecord> followRecordsForEdit(ClueResponse old, String remark, UserSession operator) {
        List<FollowRecord> records = new ArrayList<>(safeFollowRecords(old));
        String cleanedRemark = clean(remark);
        if (!StringUtils.hasText(cleanedRemark)) {
            return records;
        }
        String lastRemark = records.isEmpty() ? "" : clean(records.get(records.size() - 1).remark());
        if (!cleanedRemark.equals(lastRemark)) {
            records.add(new FollowRecord(operator.name(), operator.employeeCode(), cleanedRemark, nowText()));
        }
        return records;
    }

    private List<FollowRecord> safeFollowRecords(ClueResponse clue) {
        return clue.followRecords() == null ? new ArrayList<>() : new ArrayList<>(clue.followRecords());
    }

    private List<AssignLogRecord> safeAssignLogs(ClueResponse clue) {
        return clue.assignLogs() == null ? new ArrayList<>() : new ArrayList<>(clue.assignLogs());
    }

    private List<OperationLogRecord> safeOperationLogs(ClueResponse clue) {
        return clue.operationLogs() == null ? new ArrayList<>() : new ArrayList<>(clue.operationLogs());
    }

    private List<OperationLogRecord> operationLogsForEdit(ClueResponse old, ClueSaveRequest request, String status, UserSession operator) {
        List<OperationLogRecord> logs = new ArrayList<>(safeOperationLogs(old));
        appendFieldLog(logs, operator, "sourcePlatform", "来源平台", sourcePlatformText(old.sourcePlatform()), sourcePlatformText(normalizeSourcePlatform(request.sourcePlatform(), old.sourcePlatform())));
        appendFieldLog(logs, operator, "contactInfo", "客户联系方式", old.contactInfo(), clean(request.contactInfo()));
        appendFieldLog(logs, operator, "hasWechatId", "是否有微信号", yesNoText(old.hasWechatId()), yesNoText(hasWechatId(request.hasWechatId())));
        appendFieldLog(logs, operator, "status", "当前状态", statusText(old.status()), statusText(status));
        appendFieldLog(logs, operator, "remark", "备注", old.remark(), StringUtils.hasText(request.remark()) ? clean(request.remark()) : old.remark());
        appendFieldLog(logs, operator, "depositAmount", "定金金额", old.depositAmount(), clean(request.depositAmount()));
        appendFieldLog(logs, operator, "statusRemark", "状态备注", old.statusRemark(), clean(request.statusRemark()));
        appendFieldLog(logs, operator, "douyinImages", "抖音截图数量", String.valueOf(safeImages(old.douyinImages()).size()), String.valueOf(safeImages(request.douyinImages()).size()));
        appendFieldLog(logs, operator, "wechatImages", "微信截图数量", String.valueOf(safeImages(old.wechatImages()).size()), String.valueOf(safeImages(request.wechatImages()).size()));
        appendFieldLog(logs, operator, "refundAmount", "退单金额", old.refundAmount(), clean(request.refundAmount()));
        appendFieldLog(logs, operator, "refundedAt", "退款时间", old.refundedAt(), clean(request.refundedAt()));
        appendFieldLog(logs, operator, "landingAt", "落地时间", old.landingAt(), clean(request.landingAt()));
        appendFieldLog(logs, operator, "landingRemark", "落地备注", old.landingRemark(), clean(request.landingRemark()));
        return logs;
    }

    private List<OperationLogRecord> statusOperationLogs(ClueResponse old, UserSession operator, String status, String depositAmount, String statusRemark, String refundAmount, String refundedAt, String landingAt, String landingRemark) {
        List<OperationLogRecord> logs = new ArrayList<>(safeOperationLogs(old));
        appendFieldLog(logs, operator, "status", "当前状态", statusText(old.status()), statusText(status));
        appendFieldLog(logs, operator, "depositAmount", "定金金额", old.depositAmount(), clean(depositAmount));
        appendFieldLog(logs, operator, "statusRemark", "状态备注", old.statusRemark(), clean(statusRemark));
        appendFieldLog(logs, operator, "refundAmount", "退单金额", old.refundAmount(), clean(refundAmount));
        appendFieldLog(logs, operator, "refundedAt", "退款时间", old.refundedAt(), clean(refundedAt));
        appendFieldLog(logs, operator, "landingAt", "落地时间", old.landingAt(), clean(landingAt));
        appendFieldLog(logs, operator, "landingRemark", "落地备注", old.landingRemark(), clean(landingRemark));
        return logs;
    }

    private UserSession systemOperator() {
        return new UserSession("SYSTEM", "系统", "ADMIN", "SYSTEM", "", List.of());
    }

    private void appendFieldLog(List<OperationLogRecord> logs, UserSession operator, String field, String fieldText, String oldValue, String newValue) {
        String oldClean = clean(oldValue);
        String newClean = clean(newValue);
        if (oldClean.equals(newClean)) {
            return;
        }
        logs.add(new OperationLogRecord(
                "UPDATE_FIELD",
                "修改字段",
                operator.name(),
                operator.employeeCode(),
                field,
                fieldText,
                oldClean,
                newClean,
                nowText()
        ));
    }

    private List<AssignLogRecord> appendAssignLog(ClueResponse old, AssignLogRecord record) {
        List<AssignLogRecord> logs = new ArrayList<>(safeAssignLogs(old));
        logs.add(record);
        return logs;
    }

    private AssignLogRecord assignLog(String action, UserSession operator, String fromSales, String fromSalesCode, String toSales, String toSalesCode, String remark) {
        return new AssignLogRecord(
                action,
                assignActionText(action),
                operator.name(),
                operator.employeeCode(),
                clean(fromSales),
                clean(fromSalesCode),
                clean(toSales),
                clean(toSalesCode),
                clean(remark),
                nowText()
        );
    }

    private String assignActionText(String action) {
        return switch (action) {
            case "ASSIGN" -> "分配";
            case "TRANSFER" -> "转派";
            case "CLAIM" -> "领取";
            case "CLAIM_CONFLICT" -> "抢单冲突";
            case "RELEASE" -> "释放";
            default -> action;
        };
    }

    private String statusText(String status) {
        return switch (status) {
            case "NEW" -> "新录入";
            case "FOLLOWING" -> "跟进中";
            case "TO_DEAL" -> "跟进中";
            case "DEPOSIT_PAID", "DEALED" -> "已交定金";
            case "INVALID" -> "无效用户";
            case "REFUNDED" -> "退单";
            case "LANDED" -> "已落地";
            default -> status;
        };
    }

    private PerformanceRowResponse toPerformanceRow(UserRecord user, List<ClueResponse> clues) {
        long repeatDemandCount = clues.stream().filter(item -> Boolean.TRUE.equals(item.repeatDemand())).count();
        return new PerformanceRowResponse(
                user.employeeCode(),
                user.name(),
                user.role(),
                user.position(),
                user.leaderEmployeeCode(),
                clues.size(),
                clues.stream().filter(item -> itemDate(item).map(LocalDate.now()::equals).orElse(false)).count(),
                repeatDemandCount,
                clues.size() - repeatDemandCount,
                clues.stream().filter(item -> "DEPOSIT_PAID".equals(item.status())).count(),
                clues.stream().filter(item -> "REFUNDED".equals(item.status())).count(),
                clues.stream().filter(item -> "LANDED".equals(item.status())).count(),
                clues.stream().filter(item -> "INVALID".equals(item.status())).count()
        );
    }

    private List<ClueResponse> cluesForEmployee(List<ClueResponse> rows, String employeeCode, Map<String, UserRecord> usersByCode) {
        UserRecord user = usersByCode.get(employeeCode);
        if (user != null && "SALES".equals(user.position())) {
            return rows.stream().filter(clue -> employeeCode.equals(clue.assignedSalesEmployeeCode())).toList();
        }
        return rows.stream().filter(clue -> employeeCode.equals(resolveUploaderCode(clue, usersByCode))).toList();
    }

    private List<ClueResponse> visibleCluesForManagement(String startDate, String endDate, String token, List<UserRecord> visibleUsers, Map<String, UserRecord> usersByCode) {
        UserSession currentUser = authService.currentUser(token);
        return readAll().stream()
                .filter(item -> canViewInManagement(item, currentUser, visibleUsers, usersByCode))
                .filter(item -> matchesDateRange(item, startDate, endDate))
                .sorted(Comparator.comparing(ClueResponse::createdAt).reversed())
                .toList();
    }

    private boolean canViewInWorkspace(ClueResponse item, UserSession currentUser, Map<String, UserRecord> usersByCode) {
        if ("ADMIN".equals(currentUser.role())) {
            return true;
        }
        if ("SALES".equals(currentUser.position()) && currentUser.employeeCode().equals(item.assignedSalesEmployeeCode())) {
            return true;
        }
        return currentUser.employeeCode().equals(resolveUploaderCode(item, usersByCode));
    }

    private void ensureAssignPermission(String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_ASSIGN)) {
            throw new BusinessException("没有分配管理权限，请联系管理员开通");
        }
    }

    private String sortTime(ClueResponse item) {
        if (StringUtils.hasText(item.updatedAt())) {
            return item.updatedAt();
        }
        if (StringUtils.hasText(item.createdAt())) {
            return item.createdAt();
        }
        return "";
    }

    private boolean canViewInManagement(ClueResponse item, UserSession currentUser, List<UserRecord> visibleUsers, Map<String, UserRecord> usersByCode) {
        if ("ADMIN".equals(currentUser.role())) {
            return true;
        }
        Set<String> visibleCodes = visibleUsers.stream().map(UserRecord::employeeCode).collect(Collectors.toSet());
        if ("SALES".equals(currentUser.position())) {
            return visibleCodes.contains(item.assignedSalesEmployeeCode());
        }
        return visibleCodes.contains(resolveUploaderCode(item, usersByCode));
    }

    private boolean canViewInPublicSalesPool(ClueResponse item, UserSession currentUser, Map<String, UserRecord> usersByCode) {
        if ("ADMIN".equals(currentUser.role()) || "SALES".equals(currentUser.position())) {
            return true;
        }
        String uploaderCode = resolveUploaderCode(item, usersByCode);
        if ("LEADER".equals(currentUser.role())) {
            return currentUser.employeeCode().equals(uploaderCode)
                    || usersByCode.values().stream()
                    .filter(user -> currentUser.employeeCode().equals(user.leaderEmployeeCode()))
                    .anyMatch(user -> user.employeeCode().equals(uploaderCode));
        }
        return currentUser.employeeCode().equals(uploaderCode);
    }

    private List<UserRecord> businessVisibleUsers(String token) {
        UserSession currentUser = authService.currentUser(token);
        List<UserRecord> visibleUsers = authService.usersVisibleTo(token);
        if ("ADMIN".equals(currentUser.role())) {
            return visibleUsers;
        }
        return visibleUsers.stream()
                .filter(user -> currentUser.position().equals(user.position()))
                .toList();
    }

    private boolean matchesKeyword(ClueResponse item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return contains(item.customerCode(), keyword)
                || contains(sourcePlatformText(item.sourcePlatform()), keyword)
                || contains(item.sourcePlatform(), keyword)
                || contains(item.contactInfo(), keyword)
                || contains(item.remark(), keyword)
                || contains(item.status(), keyword)
                || contains(item.uploader(), keyword)
                || contains(item.uploaderEmployeeCode(), keyword)
                || contains(item.assignedSales(), keyword)
                || contains(item.assignedSalesEmployeeCode(), keyword);
    }

    private boolean matchesClueFilters(ClueResponse item, String customerCode, String contactInfo, String sourcePlatform, String status, String uploader, String assignedSales) {
        if (StringUtils.hasText(customerCode) && !contains(item.customerCode(), customerCode.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (StringUtils.hasText(contactInfo) && !contains(item.contactInfo(), contactInfo.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (StringUtils.hasText(sourcePlatform) && !normalizeSourcePlatform(sourcePlatform).equals(normalizeSourcePlatform(item.sourcePlatform()))) {
            return false;
        }
        if (StringUtils.hasText(status) && !normalizeStatus(status, "NEW").equals(item.status())) {
            return false;
        }
        if (StringUtils.hasText(uploader)) {
            String value = uploader.trim().toLowerCase(Locale.ROOT);
            if (!contains(item.uploader(), value) && !contains(item.uploaderEmployeeCode(), value)) {
                return false;
            }
        }
        if (StringUtils.hasText(assignedSales)) {
            String value = assignedSales.trim().toLowerCase(Locale.ROOT);
            if (!contains(item.assignedSales(), value) && !contains(item.assignedSalesEmployeeCode(), value)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesStatsDetail(ClueResponse item, String type, String value) {
        if ("ALL".equals(type)) {
            return true;
        }
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return switch (type) {
            case "STATUS" -> value.equals(item.status());
            case "UPLOADER" -> value.equals(item.uploader()) || value.equals(item.uploaderEmployeeCode());
            case "SALES" -> value.equals(item.assignedSales()) || value.equals(item.assignedSalesEmployeeCode());
            default -> true;
        };
    }

    private boolean matchesDateRange(ClueResponse item, String startDate, String endDate) {
        Optional<LocalDate> createdDate = itemDate(item);
        if (createdDate.isEmpty()) {
            return false;
        }
        LocalDate date = createdDate.get();
        LocalDate start = parseDate(startDate).orElse(null);
        LocalDate end = parseDate(endDate).orElse(LocalDate.now());
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return !date.isAfter(end);
    }

    private boolean matchesDealReportDateRange(ClueResponse item, String startDate, String endDate) {
        Optional<LocalDate> reportDate = dealReportDate(item);
        if (reportDate.isEmpty()) {
            return false;
        }
        LocalDate date = reportDate.get();
        LocalDate start = parseDate(startDate).orElse(null);
        LocalDate end = parseDate(endDate).orElse(LocalDate.now());
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return !date.isAfter(end);
    }

    private boolean matchesLogDateRange(String createdAt, String startDate, String endDate) {
        if (!StringUtils.hasText(createdAt) || createdAt.length() < 10) {
            return false;
        }
        Optional<LocalDate> logDate = parseDate(createdAt.substring(0, 10));
        if (logDate.isEmpty()) {
            return false;
        }
        LocalDate start = parseDate(startDate).orElse(null);
        LocalDate end = parseDate(endDate).orElse(LocalDate.now());
        if (start != null && logDate.get().isBefore(start)) {
            return false;
        }
        return !logDate.get().isAfter(end);
    }

    private Optional<LocalDate> dealReportDate(ClueResponse item) {
        List<StatusChangeRecord> history = safeHistory(item);
        for (int i = history.size() - 1; i >= 0; i--) {
            StatusChangeRecord record = history.get(i);
            if (item.status().equals(record.status()) && StringUtils.hasText(record.createdAt()) && record.createdAt().length() >= 10) {
                return parseDate(record.createdAt().substring(0, 10));
            }
        }
        String fallback = StringUtils.hasText(item.updatedAt()) ? item.updatedAt() : item.createdAt();
        if (!StringUtils.hasText(fallback) || fallback.length() < 10) {
            return Optional.empty();
        }
        return parseDate(fallback.substring(0, 10));
    }

    private Optional<LocalDate> itemDate(ClueResponse item) {
        if (!StringUtils.hasText(item.createdAt()) || item.createdAt().length() < 10) {
            return Optional.empty();
        }
        return parseDate(item.createdAt().substring(0, 10));
    }

    private Optional<LocalDate> parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException error) {
            return Optional.empty();
        }
    }

    private void ensureUniqueContactOnUpdate(List<ClueResponse> rows, String contactInfo, String currentCustomerCode) {
        String normalizedContact = normalizeContact(contactInfo);
        if (!StringUtils.hasText(normalizedContact)) {
            return;
        }
        Optional<ClueResponse> existing = rows.stream()
                .filter(item -> !item.customerCode().equals(currentCustomerCode))
                .filter(item -> normalizeContact(item.contactInfo()).equals(normalizedContact))
                .findFirst();
        if (existing.isPresent()) {
            throw new BusinessException("当前客户已经被" + existing.get().uploader() + "联系过，请不要重复保存");
        }
    }

    private List<ClueResponse> findSameContactRows(List<ClueResponse> rows, String normalizedContact) {
        return rows.stream()
                .filter(item -> normalizeContact(item.contactInfo()).equals(normalizedContact))
                .sorted(Comparator.comparing(ClueResponse::createdAt))
                .toList();
    }

    private String rootCustomerCode(ClueResponse clue) {
        return StringUtils.hasText(clue.originalCustomerCode()) ? clue.originalCustomerCode() : clue.customerCode();
    }

    private boolean belongsToSameCustomer(ClueResponse item, String contactKey, String rootCode) {
        if (StringUtils.hasText(contactKey)) {
            return contactKey.equals(normalizeContact(item.contactInfo()));
        }
        return rootCode.equals(rootCustomerCode(item));
    }

    private String normalizeStatus(String status, String fallback) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : fallback;
        if ("TO_DEAL".equals(normalized)) {
            return "FOLLOWING";
        }
        if ("DEALED".equals(normalized)) {
            return "DEPOSIT_PAID";
        }
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException("状态不正确");
        }
        return normalized;
    }

    private String normalizeSourcePlatform(String sourcePlatform) {
        return normalizeSourcePlatform(sourcePlatform, "DOUYIN");
    }

    private String normalizeSourcePlatform(String sourcePlatform, String fallback) {
        String normalized = StringUtils.hasText(sourcePlatform) ? sourcePlatform.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!SOURCE_PLATFORMS.contains(normalized)) {
            return "DOUYIN";
        }
        return normalized;
    }

    private String sourcePlatformText(String sourcePlatform) {
        return switch (normalizeSourcePlatform(sourcePlatform)) {
            case "XIAOHONGSHU" -> "小红书";
            default -> "抖音";
        };
    }

    private String normalizeContact(String contactInfo) {
        return contactInfo == null ? "" : contactInfo.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeEmployeeCode(String employeeCode) {
        return employeeCode == null ? "" : employeeCode.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveUploaderCode(ClueResponse clue, Map<String, UserRecord> usersByCode) {
        if (StringUtils.hasText(clue.uploaderEmployeeCode())) {
            return clue.uploaderEmployeeCode();
        }
        return usersByCode.values().stream()
                .filter(user -> user.name().equals(clue.uploader()))
                .map(UserRecord::employeeCode)
                .findFirst()
                .orElse("");
    }

    private Map<String, UserRecord> userMap(List<UserRecord> users) {
        return users.stream().collect(Collectors.toMap(UserRecord::employeeCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private String createCustomerCode(List<ClueResponse> rows, String employeeCode) {
        String prefix = employeeCode + LocalDate.now().format(DATE_CODE_FORMAT);
        long todayCount = rows.stream().filter(item -> item.customerCode().startsWith(prefix)).count();
        return prefix + String.format("%02d", todayCount + 1);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasWechatId(Boolean value) {
        return value == null || Boolean.TRUE.equals(value);
    }

    private String yesNoText(Boolean value) {
        return hasWechatId(value) ? "有" : "无";
    }

    private String defaultTime(String value) {
        return StringUtils.hasText(value) ? value.trim() : nowText();
    }

    private String appendRemark(String remark) {
        return StringUtils.hasText(remark) ? "；" + remark.trim() : "";
    }

    private List<ImageFileDto> safeImages(List<ImageFileDto> images) {
        return images == null ? List.of() : images;
    }

    private List<ClueResponse> readAll() {
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dataFile.toFile(), CLUE_LIST_TYPE).stream()
                    .map(this::normalizeClue)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException error) {
            throw new IllegalStateException("读取客户线索数据失败", error);
        }
    }

    private ClueResponse normalizeClue(ClueResponse old) {
        return new ClueResponse(
                old.customerCode(),
                normalizeSourcePlatform(old.sourcePlatform()),
                clean(old.contactInfo()),
                hasWechatId(old.hasWechatId()),
                old.uploader(),
                old.uploaderEmployeeCode(),
                normalizeStatus(old.status(), "NEW"),
                clean(old.remark()),
                safeImages(old.douyinImages()),
                safeImages(old.wechatImages()),
                Boolean.TRUE.equals(old.repeatDemand()),
                old.originalCustomerCode(),
                old.demandSequence() == null ? 1 : old.demandSequence(),
                old.assignedSales(),
                old.assignedSalesEmployeeCode(),
                clean(old.depositAmount()),
                clean(old.statusRemark()),
                clean(old.refundAmount()),
                clean(old.refundedAt()),
                clean(old.landingAt()),
                clean(old.landingRemark()),
                safeHistory(old),
                safeFollowRecords(old),
                safeAssignLogs(old),
                safeOperationLogs(old),
                old.createdAt(),
                old.updatedAt()
        );
    }

    private void writeAll(List<ClueResponse> rows) {
        try {
            Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile.toFile(), rows);
        } catch (IOException error) {
            throw new IllegalStateException("保存客户线索数据失败", error);
        }
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMAT);
    }
}
