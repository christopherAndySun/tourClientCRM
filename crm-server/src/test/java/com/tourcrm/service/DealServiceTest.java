package com.tourcrm.service;

import com.tourcrm.dto.DealResponse;
import com.tourcrm.dto.DealSaveRequest;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.UserSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealServiceTest {

    private static final String TOKEN = "token";
    private final AuthService authService = mock(AuthService.class);
    private final CustomerClueService customerClueService = mock(CustomerClueService.class);
    private final DatabaseStore databaseStore = mock(DatabaseStore.class);
    private final DealRepository dealRepository = mock(DealRepository.class);
    private final DealService service = new DealService(authService, customerClueService, databaseStore, dealRepository);

    @Test
    void createDealUsesDatabaseSequenceAndSyncsClueStatus() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_DEALS)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(salesUser());
        when(dealRepository.existsForCustomer("XA0705-01")).thenReturn(false);
        when(databaseStore.nextDealDailySequence(any(), eq("TOTAL"))).thenReturn(7);
        when(databaseStore.nextDealDailySequence(any(), eq("USER:SA"))).thenReturn(2);
        when(dealRepository.insert(any())).thenReturn(true);

        DealResponse created = service.create(saveRequest(), TOKEN);

        assertThat(created.dealCode()).startsWith("D");
        assertThat(created.totalDealSequence()).isEqualTo(7);
        assertThat(created.personalDealSequence()).isEqualTo(2);
        verify(customerClueService).markDealed("XA0705-01");
    }

    @Test
    void cancelDealWritesRefundAndSyncsClueStatus() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_DEALS)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(salesUser());
        when(dealRepository.findByCode("D0705007")).thenReturn(Optional.of(deal("DEPOSIT_PAID")));

        boolean result = service.cancel("D0705007", "客户退单", "100", "2026-07-05 12:00", TOKEN);

        assertThat(result).isTrue();
        ArgumentCaptor<DealResponse> captor = ArgumentCaptor.forClass(DealResponse.class);
        verify(dealRepository).write(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REFUNDED");
        assertThat(captor.getValue().refundAmount()).isEqualTo("100");
        verify(customerClueService).markRefunded("XA0705-01", "客户退单", "100", "2026-07-05 12:00");
    }

    @Test
    void findByCodeUsesRefundedClueStatusAsSourceOfTruth() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_DEALS)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(salesUser());
        when(dealRepository.findByCode("D0705007")).thenReturn(Optional.of(deal("DEPOSIT_PAID")));
        when(customerClueService.findByCustomerCodeForSystem("XA0705-01")).thenReturn(Optional.of(clue("REFUNDED")));

        Optional<DealResponse> result = service.findByCode("D0705007", TOKEN);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("REFUNDED");
        assertThat(result.get().refundAmount()).isEqualTo("100");
        assertThat(result.get().refundRemark()).isEqualTo("客户退单");
        assertThat(result.get().refundedAt()).isEqualTo("2026-07-05 12:00");
    }

    @Test
    void findByCodeUsesLandedClueStatusAsSourceOfTruth() {
        when(authService.hasMenuPermission(TOKEN, AuthService.MENU_DEALS)).thenReturn(true);
        when(authService.currentUser(TOKEN)).thenReturn(salesUser());
        when(dealRepository.findByCode("D0705007")).thenReturn(Optional.of(deal("DEPOSIT_PAID")));
        when(customerClueService.findByCustomerCodeForSystem("XA0705-01")).thenReturn(Optional.of(clue("LANDED")));

        Optional<DealResponse> result = service.findByCode("D0705007", TOKEN);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("LANDED");
        assertThat(result.get().landingAt()).isEqualTo("2026-07-06 12:00");
        assertThat(result.get().landingRemark()).isEqualTo("客户已落地");
    }

    private DealSaveRequest saveRequest() {
        return new DealSaveRequest("XA0705-01", "客户A", "500", "1500", "2026-07-05", "2026-07-05", "报价", "2026-08-01", "行程", "2026-07-05");
    }

    private DealResponse deal(String status) {
        return new DealResponse(
                "D0705007",
                "XA0705-01",
                "客户A",
                "500",
                "1500",
                "2026-07-05",
                "2026-07-05",
                "报价",
                "2026-08-01",
                "行程",
                "2026-07-05",
                "销售A",
                "SA",
                7,
                2,
                status,
                "",
                "",
                "",
                "",
                "",
                "2026-07-05 10:00",
                "2026-07-05 10:00"
        );
    }

    private ClueResponse clue(String status) {
        return new ClueResponse(
                "XA0705-01",
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
                false,
                "",
                1,
                "销售A",
                "SA",
                "500",
                "1500",
                "REFUNDED".equals(status) ? "客户退单" : "",
                "REFUNDED".equals(status) ? "100" : "",
                "REFUNDED".equals(status) ? "2026-07-05 12:00" : "",
                "LANDED".equals(status) ? "2026-07-06 12:00" : "",
                "LANDED".equals(status) ? "客户已落地" : "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "2026-07-05 10:00",
                "2026-07-05 10:00"
        );
    }

    private UserSession salesUser() {
        return new UserSession("销售A", "SA", "EMPLOYEE", "SALES", "", "HEADQUARTERS", null, null, List.of(AuthService.MENU_DEALS));
    }
}
