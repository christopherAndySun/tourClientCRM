package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.ClueAssignRequest;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ClueSaveRequest;
import com.tourcrm.dto.ClueStatusUpdateRequest;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerClueServiceTest {

    private static final String TOKEN = "token";
    private final AuthService authService = mock(AuthService.class);
    private final DatabaseStore databaseStore = mock(DatabaseStore.class);
    private final CustomerClueRepository customerClueRepository = mock(CustomerClueRepository.class);
    private final StatsQueryService statsQueryService = mock(StatsQueryService.class);
    private final RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
    private final DingTalkClueNotificationService dingTalkClueNotificationService = mock(DingTalkClueNotificationService.class);
    private final CustomerClueService service = new CustomerClueService(authService, databaseStore, customerClueRepository, statsQueryService, realtimeEventService, dingTalkClueNotificationService);

    @Test
    void createRejectsDuplicateContactFromCustomerProfile() {
        when(authService.currentUser(TOKEN)).thenReturn(operationUser());
        when(databaseStore.findRootCustomerCodeByContactKey("wx123")).thenReturn(Optional.of("XA0705-01"));
        when(databaseStore.findCluesByRootCustomerCode("XA0705-01")).thenReturn(List.of(clue("XA0705-01", "", 1, "NEW", "", null)));

        assertThatThrownBy(() -> service.create(saveRequest("wx123", false, false), TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请不要重复保存");

        verify(databaseStore, never()).insertClue(any());
    }

    @Test
    void createRepeatDemandUsesRootCustomerAndNextDemandSequence() {
        when(authService.currentUser(TOKEN)).thenReturn(operationUser());
        when(databaseStore.findRootCustomerCodeByContactKey("wx123")).thenReturn(Optional.of("XA0705-01"));
        when(databaseStore.findCluesByRootCustomerCode("XA0705-01")).thenReturn(List.of(
                clue("XA0705-01", "", 1, "NEW", "", null),
                clue("XB0705-02", "XA0705-01", 2, "FOLLOWING", "", null)
        ));
        when(databaseStore.nextClueDailySequence(any(), eq("HQ"))).thenReturn(3);
        when(databaseStore.insertClue(any())).thenReturn(true);
        when(databaseStore.findClueByCustomerCode(anyString())).thenReturn(Optional.empty());

        ClueResponse created = service.create(saveRequest("wx123", true, false), TOKEN);

        assertThat(created.repeatDemand()).isTrue();
        assertThat(created.originalCustomerCode()).isEqualTo("XA0705-01");
        assertThat(created.demandSequence()).isEqualTo(3);
    }

    @Test
    void updateRejectsContactOwnedByAnotherRootCustomer() {
        ClueResponse current = clue("XB0705-02", "", 1, "NEW", "", null);
        when(authService.currentUser(TOKEN)).thenReturn(operationUser());
        when(databaseStore.findClueByCustomerCodeForUpdate("XB0705-02")).thenReturn(Optional.of(current));
        when(databaseStore.findRootCustomerCodeByContactKey("wx123")).thenReturn(Optional.of("XA0705-01"));
        when(databaseStore.findCluesByRootCustomerCode("XA0705-01")).thenReturn(List.of(clue("XA0705-01", "", 1, "NEW", "", null)));

        assertThatThrownBy(() -> service.update("XB0705-02", saveRequest("wx123", false, false), TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请不要重复保存");

        verify(databaseStore, never()).writeClue(any());
    }

    @Test
    void updateRejectsDuplicateContactEvenWhenRepeatDemandIsAllowed() {
        ClueResponse current = clue("XB0705-02", "", 1, "NEW", "", null);
        when(authService.currentUser(TOKEN)).thenReturn(operationUser());
        when(databaseStore.findClueByCustomerCodeForUpdate("XB0705-02")).thenReturn(Optional.of(current));
        when(databaseStore.findRootCustomerCodeByContactKey("wx123")).thenReturn(Optional.of("XA0705-01"));
        when(databaseStore.findCluesByRootCustomerCode("XA0705-01")).thenReturn(List.of(clue("XA0705-01", "", 1, "NEW", "", null)));

        assertThatThrownBy(() -> service.update("XB0705-02", saveRequest("wx123", true, false), TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请不要重复保存");

        verify(databaseStore, never()).writeClue(any());
    }

    @Test
    void claimConflictRecordsAssignLogAndFails() {
        when(authService.currentUser(TOKEN)).thenReturn(salesUser("SA", "销售A"));
        when(databaseStore.findClueByCustomerCodeForUpdate("XA0705-01"))
                .thenReturn(Optional.of(clue("XA0705-01", "", 1, "FOLLOWING", "SB", "销售B")));

        assertThatThrownBy(() -> service.claimSalesClue("XA0705-01", TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他销售领取");

        ArgumentCaptor<ClueResponse> captor = ArgumentCaptor.forClass(ClueResponse.class);
        verify(databaseStore).writeClue(captor.capture());
        assertThat(captor.getValue().assignLogs()).anyMatch(log -> "CLAIM_CONFLICT".equals(log.action()));
    }

    @Test
    void salesCanReleaseOwnSalesPoolClue() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_ASSIGN)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(salesUser("SA", "销售A"));
        when(databaseStore.findClueByCustomerCodeForUpdate("XA0705-01"))
                .thenReturn(Optional.of(clue("XA0705-01", "", 1, "FOLLOWING", "SA", "销售A")));

        service.releaseSalesClue("XA0705-01", new ClueAssignRequest("", "客户暂不跟进"), TOKEN);

        ArgumentCaptor<ClueResponse> captor = ArgumentCaptor.forClass(ClueResponse.class);
        verify(databaseStore).writeClue(captor.capture());
        assertThat(captor.getValue().assignedSalesEmployeeCode()).isNull();
        assertThat(captor.getValue().assignedSales()).isNull();
        assertThat(captor.getValue().assignLogs()).anyMatch(log -> "RELEASE".equals(log.action()));
    }

    @Test
    void salesCannotReleaseOtherSalesPoolClue() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_ASSIGN)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(salesUser("SA", "销售A"));
        when(databaseStore.findClueByCustomerCodeForUpdate("XA0705-01"))
                .thenReturn(Optional.of(clue("XA0705-01", "", 1, "FOLLOWING", "SB", "销售B")));

        assertThatThrownBy(() -> service.releaseSalesClue("XA0705-01", new ClueAssignRequest("", "误操作"), TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能释放自己销售池里的线索");

        verify(databaseStore, never()).writeClue(any());
    }

    @Test
    void refundedStatusRequiresExistingDepositFlow() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_CLUES)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(operationUser());
        when(databaseStore.findClueByCustomerCodeForUpdate("XA0705-01"))
                .thenReturn(Optional.of(clue("XA0705-01", "", 1, "NEW", "", null)));

        assertThatThrownBy(() -> service.updateStatus("XA0705-01", new ClueStatusUpdateRequest("REFUNDED", "", "退单", "100", "2026-07-05 12:00", "", ""), TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没交定金");
    }

    @Test
    void landedStatusCanBeSavedAfterDepositFlow() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_CLUES)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(operationUser());
        when(databaseStore.findClueByCustomerCodeForUpdate("XA0705-01"))
                .thenReturn(Optional.of(clue("XA0705-01", "", 1, "DEPOSIT_PAID", "SA", "销售A")));

        service.updateStatus("XA0705-01", new ClueStatusUpdateRequest("LANDED", "500", "已落地", "", "", "2026-07-05 12:00", "完成"), TOKEN);

        ArgumentCaptor<ClueResponse> captor = ArgumentCaptor.forClass(ClueResponse.class);
        verify(databaseStore).writeClue(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("LANDED");
        assertThat(captor.getValue().landingAt()).isEqualTo("2026-07-05 12:00");
    }

    private ClueSaveRequest saveRequest(String contactInfo, boolean allowRepeatDemand, boolean hasWechatId) {
        return new ClueSaveRequest("DOUYIN", "ACTIVE", contactInfo, hasWechatId, "NEW", "测试备注", List.of(), List.of(), allowRepeatDemand, "", "", "", "", "", "", "");
    }

    private ClueResponse clue(String customerCode, String originalCustomerCode, int demandSequence, String status, String salesCode, String salesName) {
        return new ClueResponse(
                customerCode,
                "DOUYIN",
                "ACTIVE",
                "wx123",
                true,
                "小白",
                "XA",
                "HEADQUARTERS",
                null,
                null,
                status,
                "备注",
                List.of(),
                List.of(),
                originalCustomerCode != null && !originalCustomerCode.isBlank(),
                originalCustomerCode,
                demandSequence,
                salesName,
                salesCode,
                "DEPOSIT_PAID".equals(status) ? "500" : "",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "2026-07-05 10:00",
                "2026-07-05 10:00"
        );
    }

    private UserSession operationUser() {
        return new UserSession("小白", "XA", "EMPLOYEE", "OPERATION", "", "HEADQUARTERS", null, null, false, List.of(AuthService.MENU_CLUES, AuthService.MENU_CLUE_CREATE));
    }

    private UserSession salesUser(String code, String name) {
        return new UserSession(name, code, "EMPLOYEE", "SALES", "", "HEADQUARTERS", null, null, false, List.of(AuthService.MENU_ASSIGN, AuthService.MENU_DEALS));
    }

    @SuppressWarnings("unused")
    private UserRecord userRecord(String code, String name, String position) {
        return new UserRecord(name, code, "", "EMPLOYEE", position, null, "HEADQUARTERS", null, null, false, List.of(), "2026-07-05 10:00");
    }
}
