package com.tourcrm.service;

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
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.PerformanceRowResponse;
import com.tourcrm.dto.StatusChangeRecord;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private static final Set<String> STATUSES = Set.of("NEW", "FOLLOWING", "PASSED", "INVALID", "DEPOSIT_PAID", "REFUNDED", "LANDED", "DELETED");
    private static final Set<String> SOURCE_PLATFORMS = Set.of("DOUYIN", "XIAOHONGSHU");
    private static final Set<String> ADD_METHODS = Set.of("ACTIVE", "PASSIVE", "GUIDE");
    private final AuthService authService;
    private final DatabaseStore databaseStore;
    private final RealtimeEventService realtimeEventService;

    public CustomerClueService(
            AuthService authService,
            DatabaseStore databaseStore,
            RealtimeEventService realtimeEventService
    ) {
        this.authService = authService;
        this.databaseStore = databaseStore;
        this.realtimeEventService = realtimeEventService;
    }

    public PageResponse<ClueResponse> publicSalesPoolPage(String keyword, String customerCode, String contactInfo, String sourcePlatform, String addMethod, String status, String assignedSales, String startDate, String endDate, Integer page, Integer pageSize, String token) {
        ensureAssignPermission(token);
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        boolean unrestricted = "ADMIN".equals(currentUser.role()) || "SALES".equals(currentUser.position());
        List<String> visibleUploaderCodes = unrestricted ? null : publicSalesPoolUploaderCodes(currentUser, usersByCode);
        return databaseStore.queryPublicSalesPoolPage(visibleUploaderCodes, unrestricted, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, startDate, endDate, page, pageSize);
    }

    public PageResponse<ClueResponse> listPage(String keyword, String customerCode, String contactInfo, String sourcePlatform, String addMethod, String status, String uploader, String assignedSales, String startDate, String endDate, Integer page, Integer pageSize, String token) {
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        List<String> visibleUploaderCodes = workspaceUploaderCodes(currentUser, usersByCode);
        String visibleSalesCode = "SALES".equals(currentUser.position()) ? currentUser.employeeCode() : null;
        return databaseStore.queryClueListPage(visibleUploaderCodes, visibleSalesCode, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, page, pageSize);
    }

    public List<ClueResponse> listForExport(String keyword, String customerCode, String contactInfo, String sourcePlatform, String addMethod, String status, String uploader, String assignedSales, String startDate, String endDate, String token) {
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        List<String> visibleUploaderCodes = workspaceUploaderCodes(currentUser, usersByCode);
        String visibleSalesCode = "SALES".equals(currentUser.position()) ? currentUser.employeeCode() : null;
        return databaseStore.queryClueListForExport(visibleUploaderCodes, visibleSalesCode, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, 50000);
    }

    public PageResponse<ClueResponse> mySalesPoolPage(String keyword, String customerCode, String contactInfo, String sourcePlatform, String addMethod, String status, String assignedSales, String startDate, String endDate, Integer page, Integer pageSize, String token) {
        UserSession currentUser = authService.currentUser(token);
        if (!"ADMIN".equals(currentUser.role()) && !"SALES".equals(currentUser.position())) {
            throw new BusinessException("\u53ea\u6709\u7ba1\u7406\u5458\u548c\u9500\u552e\u53ef\u4ee5\u67e5\u770b\u9500\u552e\u6c60\u7ebf\u7d22");
        }
        String salesCode = "ADMIN".equals(currentUser.role()) ? clean(assignedSales).toUpperCase(Locale.ROOT) : currentUser.employeeCode();
        return databaseStore.queryMySalesPoolPage(salesCode, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, startDate, endDate, page, pageSize);
    }

    public PageResponse<AssignLogReportRow> assignLogReportPage(String customerCode, String action, String operator, String salesEmployeeCode, String startDate, String endDate, Integer page, Integer pageSize, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_ASSIGN_LOGS)) {
            throw new BusinessException("\u53ea\u6709\u7ba1\u7406\u5458\u548c\u9500\u552e\u53ef\u4ee5\u67e5\u770b\u9500\u552e\u6c60\u7ebf\u7d22");
        }
        UserSession currentUser = authService.currentUser(token);
        List<String> visibleUploaderCodes = null;
        List<String> visibleSalesCodes = null;
        if (!"ADMIN".equals(currentUser.role())) {
            List<String> visibleCodes = businessVisibleUsers(token).stream().map(UserRecord::employeeCode).toList();
            if ("SALES".equals(currentUser.position())) {
                visibleSalesCodes = visibleCodes;
            } else {
                visibleUploaderCodes = visibleCodes;
            }
        }
        return databaseStore.queryAssignLogReportPage(visibleUploaderCodes, visibleSalesCodes, customerCode, action, operator, salesEmployeeCode, startDate, endDate, page, pageSize);
    }

    public PageResponse<OperationLogReportRow> operationLogReportPage(String customerCode, String operator, String field, String startDate, String endDate, Integer page, Integer pageSize, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_OPERATION_LOGS)) {
            throw new BusinessException("\u53ea\u6709\u7ba1\u7406\u5458\u548c\u9500\u552e\u53ef\u4ee5\u67e5\u770b\u9500\u552e\u6c60\u7ebf\u7d22");
        }
        UserSession currentUser = authService.currentUser(token);
        List<String> visibleUploaderCodes = null;
        List<String> visibleSalesCodes = null;
        if (!"ADMIN".equals(currentUser.role())) {
            List<String> visibleCodes = businessVisibleUsers(token).stream().map(UserRecord::employeeCode).toList();
            if ("SALES".equals(currentUser.position())) {
                visibleSalesCodes = visibleCodes;
            } else {
                visibleUploaderCodes = visibleCodes;
            }
        }
        return databaseStore.queryOperationLogReportPage(visibleUploaderCodes, visibleSalesCodes, customerCode, operator, field, startDate, endDate, page, pageSize);
    }

    public ClueStatsResponse stats(String startDate, String endDate, String token) {
        UserSession currentUser = authService.currentUser(token);
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        List<String> visibleUploaderCodes = null;
        List<String> visibleSalesCodes = null;
        if (!"ADMIN".equals(currentUser.role())) {
            List<String> visibleCodes = visibleUsers.stream().map(UserRecord::employeeCode).toList();
            if ("SALES".equals(currentUser.position())) {
                visibleSalesCodes = visibleCodes;
            } else {
                visibleUploaderCodes = visibleCodes;
            }
        }
        return databaseStore.queryStats(visibleUploaderCodes, visibleSalesCodes, startDate, endDate);
    }

    public PageResponse<ClueResponse> statsDetailPage(String startDate, String endDate, String type, String value, Integer page, Integer pageSize, String token) {
        UserSession currentUser = authService.currentUser(token);
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        List<String> visibleUploaderCodes = null;
        List<String> visibleSalesCodes = null;
        if (!"ADMIN".equals(currentUser.role())) {
            List<String> visibleCodes = visibleUsers.stream().map(UserRecord::employeeCode).toList();
            if ("SALES".equals(currentUser.position())) {
                visibleSalesCodes = visibleCodes;
            } else {
                visibleUploaderCodes = visibleCodes;
            }
        }
        return databaseStore.queryStatsDetailPage(visibleUploaderCodes, visibleSalesCodes, type, value, startDate, endDate, page, pageSize);
    }

    public List<PerformanceRowResponse> performance(String startDate, String endDate, String token) {
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        return databaseStore.queryPerformanceRows(
                visibleUsers.stream().sorted(Comparator.comparing(UserRecord::employeeCode)).toList(),
                startDate,
                endDate
        );
    }

    public EmployeeCluesResponse employeeClues(String employeeCode, String startDate, String endDate, Integer page, Integer pageSize, String token) {
        String normalizedEmployeeCode = normalizeEmployeeCode(employeeCode);
        List<UserRecord> visibleUsers = businessVisibleUsers(token);
        Map<String, UserRecord> usersByCode = userMap(visibleUsers);
        UserRecord user = usersByCode.get(normalizedEmployeeCode);
        if (user == null) {
            throw new BusinessException("员工不存在或无权查看");
        }
        boolean sales = "SALES".equals(user.position());
        return new EmployeeCluesResponse(databaseStore.queryPerformanceRow(user, startDate, endDate), databaseStore.queryEmployeeCluesPage(normalizedEmployeeCode, sales, startDate, endDate, page, pageSize));
    }

    public Optional<ClueResponse> findByCustomerCode(String customerCode, String token) {
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        List<UserRecord> visibleUsers = authService.usersVisibleTo(token);
        return findMutableClue(customerCode)
                .filter(item -> canViewInWorkspace(item, currentUser, usersByCode)
                        || canViewInManagement(item, currentUser, visibleUsers, usersByCode));
    }

    Optional<ClueResponse> findByCustomerCodeForSystem(String customerCode) {
        return findMutableClue(customerCode).filter(this::notDeleted);
    }

    public CustomerHistoryResponse customerHistory(String customerCode, String token) {
        UserSession currentUser = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        ClueResponse current = findMutableClue(customerCode)
                .filter(this::notDeleted)
                .orElseThrow(() -> new BusinessException("客户线索不存在"));
        String contactKey = normalizeContact(current.contactInfo());
        String rootCode = rootCustomerCode(current);
        List<ClueResponse> candidateDemands = findCustomerDemandRows(contactKey, rootCode);
        List<ClueResponse> demands = candidateDemands.stream()
                .map(this::normalizeClue)
                .filter(item -> canViewInWorkspace(item, currentUser, usersByCode))
                .sorted(Comparator.comparing(item -> item.demandSequence() == null ? 1 : item.demandSequence()))
                .toList();
        String customerKey = StringUtils.hasText(contactKey) ? contactKey : rootCode;
        return new CustomerHistoryResponse(customerKey, demands.size(), demands);
    }

    @Transactional
    public ClueResponse create(ClueSaveRequest request, String token) {
        UserSession currentUser = authService.currentUser(token);
        String normalizedContact = normalizeContact(request.contactInfo());
        databaseStore.acquireContactLock(normalizedContact);
        List<ClueResponse> sameCustomerRows = findCustomerDemandRows(normalizedContact, null).stream()
                .map(this::normalizeClue)
                .toList();
        if (!sameCustomerRows.isEmpty() && !Boolean.TRUE.equals(request.allowRepeatDemand())) {
            ClueResponse first = sameCustomerRows.get(0);
            throw new BusinessException("\u5f53\u524d\u5ba2\u6237\u5df2\u7ecf\u88ab" + first.uploader() + "\u8054\u7cfb\u8fc7\uff0c\u8bf7\u4e0d\u8981\u91cd\u590d\u4fdd\u5b58");
        }

        String status = normalizeStatus(request.status(), "NEW");
        ensureFinalStatusHasDepositFlow(null, status);
        String now = nowText();
        boolean repeatDemand = !sameCustomerRows.isEmpty();
        String originalCustomerCode = repeatDemand ? rootCustomerCode(sameCustomerRows.get(0)) : null;
        int demandSequence = repeatDemand ? nextDemandSequence(sameCustomerRows) : 1;
        for (int attempt = 0; attempt < 30; attempt++) {
            int sequence = nextCustomerSequence(currentUser);
            ClueResponse clue = buildNewClue(
                    request,
                    currentUser,
                    status,
                    repeatDemand,
                    originalCustomerCode,
                    demandSequence,
                    now,
                    createCustomerCode(currentUser.employeeCode(), sequence)
            );
            if (databaseStore.insertClue(clue)) {
                ClueResponse saved = findByCustomerCodeForSystem(clue.customerCode()).orElse(clue);
                publishClueCreated(saved);
                return saved;
            }
        }
        throw new BusinessException("客户编号生成冲突，请稍后重试");
    }

    @Transactional
    public Optional<ClueResponse> update(String customerCode, ClueSaveRequest request, String token) {
        UserSession currentUser = authService.currentUser(token);
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            databaseStore.acquireContactLock(normalizeContact(request.contactInfo()));
            ensureUniqueContactOnUpdate(request.contactInfo(), old);
            String status = normalizeStatus(request.status(), old.status());
            ensureFinalStatusHasDepositFlow(old, status);
            ClueResponse updated = new ClueResponse(
                    old.customerCode(),
                    normalizeSourcePlatform(request.sourcePlatform(), old.sourcePlatform()),
                    normalizeAddMethod(request.addMethod(), old.addMethod()),
                    clean(request.contactInfo()),
                    hasWechatId(request.hasWechatId()),
                    old.uploader(),
                    old.uploaderEmployeeCode(),
                    old.orgType(),
                    old.branchId(),
                    old.branchName(),
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
                    clean(request.remainingBalance()),
                    clean(request.statusRemark()),
                    clean(request.refundAmount()),
                    clean(request.refundedAt()),
                    clean(request.landingAt()),
                    clean(request.landingRemark()),
                    statusHistoryForEdit(old, status, clean(request.depositAmount()), clean(request.statusRemark()), currentUser),
                    followRecordsForEdit(old, request.remark(), currentUser),
                    safeAssignLogs(old),
                    operationLogsForEdit(old, request, status, currentUser),
                    old.createdAt(),
                    nowText()
            );
            databaseStore.writeClue(updated);
            publishClueChanged("CLUE_UPDATED", customerCode, "\u5ba2\u8d44\u5df2\u66f4\u65b0", "\u5ba2\u6237\u7ebf\u7d22\u5df2\u66f4\u65b0");
            return findByCustomerCodeForSystem(customerCode).or(() -> Optional.of(updated));
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<ClueResponse> assignSales(String customerCode, ClueAssignRequest request, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_ASSIGN)) {
            throw new BusinessException("\u53ea\u6709\u7ba1\u7406\u5458\u548c\u9500\u552e\u53ef\u4ee5\u67e5\u770b\u9500\u552e\u6c60\u7ebf\u7d22");
        }
        UserSession currentUser = authService.currentUser(token);
        UserRecord sales = authService.listAllUsersForSystem().stream()
                .filter(user -> user.employeeCode().equals(normalizeEmployeeCode(request.salesEmployeeCode())))
                .filter(user -> "SALES".equals(user.position()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("\u9500\u552e\u8d26\u53f7\u4e0d\u5b58\u5728"));
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            String action = StringUtils.hasText(old.assignedSalesEmployeeCode()) ? "TRANSFER" : "ASSIGN";
            String remark = ("TRANSFER".equals(action) ? "\u8f6c\u6d3e\u7ed9 " : "\u5206\u914d\u7ed9 ") + sales.name() + appendRemark(request.remark());
            List<OperationLogRecord> operationLogs = assignOperationLogs(old, currentUser, sales.name(), sales.employeeCode());
            ClueResponse updated = copyWithStatusAndLogs(
                    old,
                    "FOLLOWING",
                    sales.name(),
                    sales.employeeCode(),
                    old.depositAmount(),
                    remark,
                    old.refundAmount(),
                    old.refundedAt(),
                    old.landingAt(),
                    old.landingRemark(),
                    appendStatusHistory(old, statusRecord("FOLLOWING", currentUser, old.depositAmount(), remark)),
                    appendAssignLog(old, assignLog(action, currentUser, old.assignedSales(), old.assignedSalesEmployeeCode(), sales.name(), sales.employeeCode(), request.remark())),
                    operationLogs
            );
            databaseStore.writeClue(updated);
            publishAssignChanged("ASSIGN_CHANGED", customerCode, "\u5206\u914d\u5df2\u66f4\u65b0", "\u9500\u552e\u6c60\u5206\u914d\u5df2\u66f4\u65b0");
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<ClueResponse> claimSalesClue(String customerCode, String token) {
        UserSession currentUser = authService.currentUser(token);
        if (!"SALES".equals(currentUser.position())) {
            throw new BusinessException("\u53ea\u6709\u9500\u552e\u53ef\u4ee5\u9886\u53d6\u9500\u552e\u6c60\u7ebf\u7d22");
        }
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            if (StringUtils.hasText(old.assignedSalesEmployeeCode())) {
                databaseStore.writeClue(copyWithAssignLogs(old, appendAssignLog(old, assignLog(
                        "CLAIM_CONFLICT",
                        currentUser,
                        old.assignedSales(),
                        old.assignedSalesEmployeeCode(),
                        currentUser.name(),
                        currentUser.employeeCode(),
                        "\u9886\u53d6\u51b2\u7a81"
                ))));
                throw new BusinessException("\u8be5\u7ebf\u7d22\u5df2\u88ab\u5176\u4ed6\u9500\u552e\u9886\u53d6\uff0c\u8bf7\u5237\u65b0\u540e\u67e5\u770b");
            }
            String remark = "\u9500\u552e\u9886\u53d6\uff1a" + currentUser.name();
            List<OperationLogRecord> operationLogs = assignOperationLogs(old, currentUser, currentUser.name(), currentUser.employeeCode());
            ClueResponse updated = copyWithStatusAndLogs(
                    old,
                    "FOLLOWING",
                    currentUser.name(),
                    currentUser.employeeCode(),
                    old.depositAmount(),
                    remark,
                    old.refundAmount(),
                    old.refundedAt(),
                    old.landingAt(),
                    old.landingRemark(),
                    appendStatusHistory(old, statusRecord("FOLLOWING", currentUser, old.depositAmount(), remark)),
                    appendAssignLog(old, assignLog("CLAIM", currentUser, old.assignedSales(), old.assignedSalesEmployeeCode(), currentUser.name(), currentUser.employeeCode(), remark)),
                    operationLogs
            );
            databaseStore.writeClue(updated);
            publishAssignChanged("ASSIGN_CHANGED", customerCode, "\u7ebf\u7d22\u5df2\u9886\u53d6", "\u9500\u552e\u6c60\u7ebf\u7d22\u5df2\u88ab\u9886\u53d6");
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<ClueResponse> releaseSalesClue(String customerCode, ClueAssignRequest request, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_ASSIGN)) {
            throw new BusinessException("\u53ea\u6709\u7ba1\u7406\u5458\u548c\u9500\u552e\u53ef\u4ee5\u67e5\u770b\u9500\u552e\u6c60\u7ebf\u7d22");
        }
        UserSession currentUser = authService.currentUser(token);
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            if (!StringUtils.hasText(old.assignedSalesEmployeeCode())) {
                throw new BusinessException("\u5f53\u524d\u7ebf\u7d22\u5c1a\u672a\u5206\u914d\u9500\u552e\uff0c\u65e0\u9700\u91ca\u653e");
            }
            String remark = "\u91ca\u653e\u9500\u552e\u7ebf\u7d22" + appendRemark(request.remark());
            List<OperationLogRecord> operationLogs = assignOperationLogs(old, currentUser, null, null);
            ClueResponse updated = copyWithStatusAndLogs(
                    old,
                    "FOLLOWING",
                    null,
                    null,
                    old.depositAmount(),
                    remark,
                    old.refundAmount(),
                    old.refundedAt(),
                    old.landingAt(),
                    old.landingRemark(),
                    appendStatusHistory(old, statusRecord("FOLLOWING", currentUser, old.depositAmount(), remark)),
                    appendAssignLog(old, assignLog("RELEASE", currentUser, old.assignedSales(), old.assignedSalesEmployeeCode(), null, null, request.remark())),
                    operationLogs
            );
            databaseStore.writeClue(updated);
            publishAssignChanged("ASSIGN_RELEASED", customerCode, "\u7ebf\u7d22\u5df2\u91ca\u653e", "\u6709\u7ebf\u7d22\u91ca\u653e\u56de\u516c\u5171\u6c60");
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<ClueResponse> updateStatus(String customerCode, ClueStatusUpdateRequest request, String token) {
        if (!authService.hasMenuPermission(token, AuthService.MENU_CLUES) && !authService.hasMenuPermission(token, AuthService.MENU_DEALS)) {
            throw new BusinessException("没有客户线索或成交记录权限，请联系管理员开通");
        }
        if (!authService.hasMenuPermission(token, AuthService.MENU_CLUES) && !authService.hasMenuPermission(token, AuthService.MENU_DEALS)) {
            throw new BusinessException("没有客户线索或成交记录权限，请联系管理员开通");
        }
        UserSession currentUser = authService.currentUser(token);
        String status = normalizeStatus(request.status(), "");
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            ensureFinalStatusHasDepositFlow(old, status);
            String depositAmount = StringUtils.hasText(request.depositAmount()) ? clean(request.depositAmount()) : old.depositAmount();
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
            databaseStore.writeClue(updated);
            publishClueChanged("CLUE_UPDATED", customerCode, "\u72b6\u6001\u5df2\u66f4\u65b0", "\u5ba2\u6237\u72b6\u6001\u5df2\u66f4\u65b0");
            return Optional.of(updated);
        }
        return Optional.empty();
    }

    @Transactional
    public boolean delete(String customerCode, String token) {
        UserSession operator = authService.currentUser(token);
        Map<String, UserRecord> usersByCode = userMap(authService.listAllUsersForSystem());
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent() && !"DELETED".equals(oldOptional.get().status())) {
            ClueResponse old = oldOptional.get();
            if (!canViewInWorkspace(old, operator, usersByCode)) {
                throw new BusinessException("\u65e0\u6743\u5220\u9664\u8be5\u5ba2\u6237\u7ebf\u7d22");
            }
            StatusChangeRecord record = new StatusChangeRecord("DELETED", "\u5df2\u5220\u9664", operator.name(), operator.employeeCode(), old.depositAmount(), "\u5220\u9664\u5ba2\u6237\u7ebf\u7d22", nowText());
            List<OperationLogRecord> logs = new ArrayList<>(safeOperationLogs(old));
            appendFieldLog(logs, operator, "status", "\u5f53\u524d\u72b6\u6001", statusText(old.status()), "\u5df2\u5220\u9664");
            databaseStore.writeClue(copyWithStatusAndLogs(old, "DELETED", old.assignedSales(), old.assignedSalesEmployeeCode(), old.depositAmount(), old.statusRemark(), old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark(), appendStatusHistory(old, record), logs));
            publishClueChanged("CLUE_DELETED", customerCode, "\u5ba2\u8d44\u5df2\u5220\u9664", "\u5ba2\u6237\u7ebf\u7d22\u5df2\u5220\u9664");
            return true;
        }
        return false;
    }

    @Transactional
    void markDealed(String customerCode) {
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            StatusChangeRecord record = new StatusChangeRecord("DEPOSIT_PAID", statusText("DEPOSIT_PAID"), "\u7cfb\u7edf", "SYSTEM", old.depositAmount(), "\u6210\u4ea4\u72b6\u6001\u540c\u6b65", nowText());
            List<OperationLogRecord> logs = statusOperationLogs(old, systemOperator(), "DEPOSIT_PAID", old.depositAmount(), old.statusRemark(), old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark());
            databaseStore.writeClue(copyWithStatusAndLogs(old, "DEPOSIT_PAID", old.assignedSales(), old.assignedSalesEmployeeCode(), old.depositAmount(), old.statusRemark(), old.refundAmount(), old.refundedAt(), old.landingAt(), old.landingRemark(), appendStatusHistory(old, record), logs));
            return;
        }
        throw new BusinessException("\u5ba2\u6237\u7ebf\u7d22\u4e0d\u5b58\u5728");
    }

    @Transactional
    void markRefunded(String customerCode, String remark, String refundAmount, String refundedAt) {
        Optional<ClueResponse> oldOptional = findMutableClueForUpdate(customerCode);
        if (oldOptional.isPresent()) {
            ClueResponse old = oldOptional.get();
            String finalRemark = clean(remark);
            String finalRefundAmount = clean(refundAmount);
            String finalRefundedAt = defaultTime(refundedAt);
            StatusChangeRecord record = new StatusChangeRecord("REFUNDED", statusText("REFUNDED"), "\u7cfb\u7edf", "SYSTEM", old.depositAmount(), finalRemark, nowText());
            List<OperationLogRecord> logs = statusOperationLogs(old, systemOperator(), "REFUNDED", old.depositAmount(), finalRemark, finalRefundAmount, finalRefundedAt, old.landingAt(), old.landingRemark());
            databaseStore.writeClue(copyWithStatusAndLogs(old, "REFUNDED", old.assignedSales(), old.assignedSalesEmployeeCode(), old.depositAmount(), finalRemark, finalRefundAmount, finalRefundedAt, old.landingAt(), old.landingRemark(), appendStatusHistory(old, record), logs));
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
                normalizeAddMethod(old.addMethod()),
                old.contactInfo(),
                hasWechatId(old.hasWechatId()),
                old.uploader(),
                old.uploaderEmployeeCode(),
                old.orgType(),
                old.branchId(),
                old.branchName(),
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
                old.remainingBalance(),
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
                normalizeAddMethod(old.addMethod()),
                old.contactInfo(),
                hasWechatId(old.hasWechatId()),
                old.uploader(),
                old.uploaderEmployeeCode(),
                old.orgType(),
                old.branchId(),
                old.branchName(),
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
                old.remainingBalance(),
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

    private List<StatusChangeRecord> statusHistoryForEdit(ClueResponse old, String status, String depositAmount, String statusRemark, UserSession operator) {
        boolean changed = !status.equals(old.status())
                || !clean(depositAmount).equals(clean(old.depositAmount()))
                || !clean(statusRemark).equals(clean(old.statusRemark()));
        if (!changed) {
            return safeHistory(old);
        }
        return appendStatusHistory(old, statusRecord(status, operator, depositAmount, statusRemark));
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
        appendFieldLog(logs, operator, "sourcePlatform", "\u6765\u6e90\u5e73\u53f0", sourcePlatformText(old.sourcePlatform()), sourcePlatformText(normalizeSourcePlatform(request.sourcePlatform(), old.sourcePlatform())));
        appendFieldLog(logs, operator, "addMethod", "\u6dfb\u52a0\u65b9\u5f0f", addMethodText(old.addMethod()), addMethodText(normalizeAddMethod(request.addMethod(), old.addMethod())));
        appendFieldLog(logs, operator, "contactInfo", "\u5ba2\u6237\u8054\u7cfb\u65b9\u5f0f", old.contactInfo(), clean(request.contactInfo()));
        appendFieldLog(logs, operator, "hasWechatId", "\u662f\u5426\u6709\u5fae\u4fe1\u53f7", yesNoText(old.hasWechatId()), yesNoText(hasWechatId(request.hasWechatId())));
        appendFieldLog(logs, operator, "status", "\u5f53\u524d\u72b6\u6001", statusText(old.status()), statusText(status));
        appendFieldLog(logs, operator, "remark", "\u5907\u6ce8", old.remark(), StringUtils.hasText(request.remark()) ? clean(request.remark()) : old.remark());
        appendFieldLog(logs, operator, "depositAmount", "\u5b9a\u91d1\u91d1\u989d", old.depositAmount(), clean(request.depositAmount()));
        appendFieldLog(logs, operator, "remainingBalance", "\u5269\u4f59\u5c3e\u6b3e", old.remainingBalance(), clean(request.remainingBalance()));
        appendFieldLog(logs, operator, "statusRemark", "\u72b6\u6001\u5907\u6ce8", old.statusRemark(), clean(request.statusRemark()));
        appendFieldLog(logs, operator, "douyinImages", "\u6296\u97f3\u622a\u56fe\u6570\u91cf", String.valueOf(safeImages(old.douyinImages()).size()), String.valueOf(safeImages(request.douyinImages()).size()));
        appendFieldLog(logs, operator, "wechatImages", "\u5fae\u4fe1\u622a\u56fe\u6570\u91cf", String.valueOf(safeImages(old.wechatImages()).size()), String.valueOf(safeImages(request.wechatImages()).size()));
        appendFieldLog(logs, operator, "refundAmount", "\u9000\u5355\u91d1\u989d", old.refundAmount(), clean(request.refundAmount()));
        appendFieldLog(logs, operator, "refundedAt", "\u9000\u6b3e\u65f6\u95f4", old.refundedAt(), clean(request.refundedAt()));
        appendFieldLog(logs, operator, "landingAt", "\u843d\u5730\u65f6\u95f4", old.landingAt(), clean(request.landingAt()));
        appendFieldLog(logs, operator, "landingRemark", "\u843d\u5730\u5907\u6ce8", old.landingRemark(), clean(request.landingRemark()));
        return logs;
    }

    private List<OperationLogRecord> statusOperationLogs(ClueResponse old, UserSession operator, String status, String depositAmount, String statusRemark, String refundAmount, String refundedAt, String landingAt, String landingRemark) {
        List<OperationLogRecord> logs = new ArrayList<>(safeOperationLogs(old));
        appendFieldLog(logs, operator, "status", "\u5f53\u524d\u72b6\u6001", statusText(old.status()), statusText(status));
        appendFieldLog(logs, operator, "depositAmount", "\u5b9a\u91d1\u91d1\u989d", old.depositAmount(), clean(depositAmount));
        appendFieldLog(logs, operator, "statusRemark", "\u72b6\u6001\u5907\u6ce8", old.statusRemark(), clean(statusRemark));
        appendFieldLog(logs, operator, "refundAmount", "\u9000\u5355\u91d1\u989d", old.refundAmount(), clean(refundAmount));
        appendFieldLog(logs, operator, "refundedAt", "\u9000\u6b3e\u65f6\u95f4", old.refundedAt(), clean(refundedAt));
        appendFieldLog(logs, operator, "landingAt", "\u843d\u5730\u65f6\u95f4", old.landingAt(), clean(landingAt));
        appendFieldLog(logs, operator, "landingRemark", "\u843d\u5730\u5907\u6ce8", old.landingRemark(), clean(landingRemark));
        return logs;
    }

    private List<OperationLogRecord> assignOperationLogs(ClueResponse old, UserSession operator, String salesName, String salesCode) {
        List<OperationLogRecord> logs = new ArrayList<>(safeOperationLogs(old));
        appendFieldLog(logs, operator, "assignedSales", "\u9500\u552e\u5f52\u5c5e", userText(old.assignedSales(), old.assignedSalesEmployeeCode()), userText(salesName, salesCode));
        appendFieldLog(logs, operator, "status", "\u5f53\u524d\u72b6\u6001", statusText(old.status()), statusText("FOLLOWING"));
        return logs;
    }

    private String userText(String name, String code) {
        if (!StringUtils.hasText(name) && !StringUtils.hasText(code)) {
            return "";
        }
        return clean(name) + "(" + clean(code) + ")";
    }

    private UserSession systemOperator() {
        return new UserSession("\u7cfb\u7edf", "SYSTEM", "ADMIN", "SYSTEM", "", "HEADQUARTERS", null, null, List.of());
    }

    private void appendFieldLog(List<OperationLogRecord> logs, UserSession operator, String field, String fieldText, String oldValue, String newValue) {
        String oldClean = clean(oldValue);
        String newClean = clean(newValue);
        if (oldClean.equals(newClean)) {
            return;
        }
        logs.add(new OperationLogRecord(
                "UPDATE_FIELD",
                "\u4fee\u6539\u5b57\u6bb5",
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
            case "ASSIGN" -> "\u5206\u914d";
            case "TRANSFER" -> "\u8f6c\u6d3e";
            case "CLAIM" -> "\u9886\u53d6";
            case "CLAIM_CONFLICT" -> "\u62a2\u5355\u51b2\u7a81";
            case "RELEASE" -> "\u91ca\u653e";
            default -> action;
        };
    }

    private String statusText(String status) {
        return switch (status) {
            case "NEW" -> "\u65b0\u5f55\u5165";
            case "FOLLOWING", "TO_DEAL" -> "\u8ddf\u8fdb\u4e2d";
            case "PASSED" -> "\u5df2\u901a\u8fc7";
            case "DEPOSIT_PAID", "DEALED" -> "\u5df2\u4ea4\u5b9a\u91d1";
            case "INVALID" -> "\u65e0\u6548\u7528\u6237";
            case "REFUNDED" -> "\u9000\u5355";
            case "LANDED" -> "\u5df2\u843d\u5730";
            case "DELETED" -> "\u5df2\u5220\u9664";
            default -> status;
        };
    }

    private void publishClueCreated(ClueResponse clue) {
        realtimeEventService.publish(
                "CLUE_CREATED",
                clue.customerCode(),
                "\u65b0\u5ba2\u8d44\u5165\u5e93",
                "\u6709\u65b0\u5ba2\u8d44\u8fdb\u5165\u7cfb\u7edf",
                List.of(RealtimeEventService.TARGET_ASSIGN, RealtimeEventService.TARGET_THIRD_PARTY_POOL)
        );
    }

    private void publishAssignChanged(String type, String customerCode, String title, String message) {
        realtimeEventService.publish(
                type,
                customerCode,
                title,
                message,
                List.of(RealtimeEventService.TARGET_ASSIGN)
        );
    }

    private void publishClueChanged(String type, String customerCode, String title, String message) {
        realtimeEventService.publish(
                type,
                customerCode,
                title,
                message,
                List.of(RealtimeEventService.TARGET_ASSIGN, RealtimeEventService.TARGET_THIRD_PARTY_POOL)
        );
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

    private List<String> workspaceUploaderCodes(UserSession currentUser, Map<String, UserRecord> usersByCode) {
        if ("ADMIN".equals(currentUser.role())) {
            return usersByCode.keySet().stream().toList();
        }
        return List.of(currentUser.employeeCode());
    }

    private List<String> publicSalesPoolUploaderCodes(UserSession currentUser, Map<String, UserRecord> usersByCode) {
        if ("LEADER".equals(currentUser.role())) {
            List<String> memberCodes = usersByCode.values().stream()
                    .filter(user -> currentUser.employeeCode().equals(user.leaderEmployeeCode()))
                    .map(UserRecord::employeeCode)
                    .toList();
            List<String> codes = new ArrayList<>();
            codes.add(currentUser.employeeCode());
            codes.addAll(memberCodes);
            return codes;
        }
        return List.of(currentUser.employeeCode());
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

    private List<ClueResponse> findCustomerDemandRows(String contactKey, String fallbackRootCustomerCode) {
        Optional<String> profileRoot = databaseStore.findRootCustomerCodeByContactKey(contactKey);
        if (profileRoot.isPresent()) {
            return databaseStore.findCluesByRootCustomerCode(profileRoot.get());
        }
        if (StringUtils.hasText(contactKey)) {
            List<ClueResponse> contactRows = databaseStore.findCluesByContactKey(contactKey);
            if (!contactRows.isEmpty()) {
                return contactRows;
            }
        }
        if (StringUtils.hasText(fallbackRootCustomerCode)) {
            return databaseStore.findCluesByRootCustomerCode(fallbackRootCustomerCode);
        }
        return List.of();
    }

    private int nextDemandSequence(List<ClueResponse> rows) {
        return rows.stream()
                .map(ClueResponse::demandSequence)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(rows.size()) + 1;
    }

    private void ensureUniqueContactOnUpdate(String contactInfo, ClueResponse currentClue) {
        String normalizedContact = normalizeContact(contactInfo);
        if (!StringUtils.hasText(normalizedContact)) {
            return;
        }
        String currentRoot = rootCustomerCode(currentClue);
        Optional<String> existingRoot = databaseStore.findRootCustomerCodeByContactKey(normalizedContact);
        if (existingRoot.isPresent() && !existingRoot.get().equals(currentRoot)) {
            ClueResponse owner = databaseStore.findCluesByRootCustomerCode(existingRoot.get()).stream()
                    .map(this::normalizeClue)
                    .findFirst()
                    .orElse(null);
            String ownerName = owner == null ? "其他运营" : owner.uploader();
            throw new BusinessException("\u5f53\u524d\u5ba2\u6237\u5df2\u7ecf\u88ab" + ownerName + "\u8054\u7cfb\u8fc7\uff0c\u8bf7\u4e0d\u8981\u91cd\u590d\u4fdd\u5b58");
        }
        Optional<ClueResponse> existing = databaseStore.findCluesByContactKey(normalizedContact).stream()
                .map(this::normalizeClue)
                .filter(item -> !rootCustomerCode(item).equals(currentRoot))
                .findFirst();
        if (existing.isPresent()) {
            throw new BusinessException("\u5f53\u524d\u5ba2\u6237\u5df2\u7ecf\u88ab" + existing.get().uploader() + "\u8054\u7cfb\u8fc7\uff0c\u8bf7\u4e0d\u8981\u91cd\u590d\u4fdd\u5b58");
        }
    }

    private Optional<ClueResponse> findMutableClue(String customerCode) {
        return databaseStore.findClueByCustomerCode(customerCode)
                .map(this::normalizeClue);
    }

    private Optional<ClueResponse> findMutableClueForUpdate(String customerCode) {
        return databaseStore.findClueByCustomerCodeForUpdate(customerCode)
                .map(this::normalizeClue);
    }

    private String rootCustomerCode(ClueResponse clue) {
        return StringUtils.hasText(clue.originalCustomerCode()) ? clue.originalCustomerCode() : clue.customerCode();
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
            throw new BusinessException("客户状态不合法");
        }
        return normalized;
    }

    private void ensureFinalStatusHasDepositFlow(ClueResponse old, String nextStatus) {
        if (!"REFUNDED".equals(nextStatus) && !"LANDED".equals(nextStatus)) {
            return;
        }
        if (hasDepositFlow(old)) {
            return;
        }
        throw new BusinessException("\u8be5\u8ba2\u5355\u6ca1\u4ea4\u5b9a\u91d1\uff0c\u4e0d\u80fd\u76f4\u63a5" + statusText(nextStatus));
    }

    private boolean hasDepositFlow(ClueResponse clue) {
        if (clue == null) {
            return false;
        }
        String currentStatus = normalizeStatus(clue.status(), "NEW");
        if ("DEPOSIT_PAID".equals(currentStatus)
                || "REFUNDED".equals(currentStatus)
                || "LANDED".equals(currentStatus)
                || StringUtils.hasText(clean(clue.depositAmount()))) {
            return true;
        }
        return safeHistory(clue).stream()
                .anyMatch(record -> "DEPOSIT_PAID".equals(normalizeStatus(record.status(), "NEW"))
                        || StringUtils.hasText(clean(record.depositAmount())));
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
            case "XIAOHONGSHU" -> "\u5c0f\u7ea2\u4e66";
            default -> "\u6296\u97f3";
        };
    }

    private String normalizeAddMethod(String addMethod) {
        return normalizeAddMethod(addMethod, "ACTIVE");
    }

    private String normalizeAddMethod(String addMethod, String fallback) {
        String normalized = StringUtils.hasText(addMethod) ? addMethod.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!ADD_METHODS.contains(normalized)) {
            return "ACTIVE";
        }
        return normalized;
    }

    private String addMethodText(String addMethod) {
        return switch (normalizeAddMethod(addMethod)) {
            case "PASSIVE" -> "\u88ab\u52a8";
            case "GUIDE" -> "\u9886\u961f";
            default -> "\u4e3b\u52a8";
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

    private ClueResponse buildNewClue(
            ClueSaveRequest request,
            UserSession currentUser,
            String status,
            boolean repeatDemand,
            String originalCustomerCode,
            int demandSequence,
            String now,
            String customerCode
    ) {
        return new ClueResponse(
                customerCode,
                normalizeSourcePlatform(request.sourcePlatform()),
                normalizeAddMethod(request.addMethod()),
                clean(request.contactInfo()),
                hasWechatId(request.hasWechatId()),
                currentUser.name(),
                currentUser.employeeCode(),
                currentUser.orgType(),
                currentUser.branchId(),
                currentUser.branchName(),
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
                clean(request.remainingBalance()),
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
    }

    private int nextCustomerSequence(UserSession currentUser) {
        return databaseStore.nextClueDailySequence(LocalDate.now(), sequenceScope(currentUser));
    }

    private String createCustomerCode(String employeeCode, int sequence) {
        String dateCode = LocalDate.now().format(DATE_CODE_FORMAT);
        return employeeCode + dateCode + "-" + String.format("%02d", sequence);
    }

    private String sequenceScope(UserSession currentUser) {
        if ("BRANCH".equals(currentUser.orgType()) && StringUtils.hasText(currentUser.branchId())) {
            return "BRANCH:" + currentUser.branchId().trim().toUpperCase(Locale.ROOT);
        }
        return "HQ";
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
        return hasWechatId(value) ? "\u6709" : "\u65e0";
    }

    private String defaultTime(String value) {
        return StringUtils.hasText(value) ? value.trim() : nowText();
    }

    private String appendRemark(String remark) {
        return StringUtils.hasText(remark) ? "\uff0c\u5907\u6ce8\uff1a" + remark.trim() : "";
    }

    private List<ImageFileDto> safeImages(List<ImageFileDto> images) {
        return images == null ? List.of() : images;
    }

    private ClueResponse normalizeClue(ClueResponse old) {
        return new ClueResponse(
                old.customerCode(),
                normalizeSourcePlatform(old.sourcePlatform()),
                normalizeAddMethod(old.addMethod()),
                clean(old.contactInfo()),
                hasWechatId(old.hasWechatId()),
                old.uploader(),
                old.uploaderEmployeeCode(),
                old.orgType(),
                old.branchId(),
                old.branchName(),
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
                clean(old.remainingBalance()),
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

    private boolean notDeleted(ClueResponse item) {
        return item != null && !"DELETED".equals(item.status());
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMAT);
    }
}
