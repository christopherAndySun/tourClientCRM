package com.tourcrm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.config.ApiAuthenticationFilter;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.ThirdPartyDownloadFailureRow;
import com.tourcrm.dto.ThirdPartyDownloadResponse;
import com.tourcrm.dto.UserSession;
import com.tourcrm.service.AuthService;
import com.tourcrm.service.AuthTokenSupport;
import com.tourcrm.service.SystemAuditService;
import com.tourcrm.service.ThirdPartyDownloadService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ThirdPartyDownloadControllerApiTest {

    private final ThirdPartyDownloadService thirdPartyDownloadService = mock(ThirdPartyDownloadService.class);
    private final SystemAuditService systemAuditService = mock(SystemAuditService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThirdPartyDownloadController(thirdPartyDownloadService, systemAuditService))
            .addFilters(new ApiAuthenticationFilter(authService, new ObjectMapper()))
            .build();

    @Test
    void pendingReturnsPagedDownloadPoolRows() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user());
        when(thirdPartyDownloadService.page(
                anyBoolean(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(), any(), nullable(String.class)
        )).thenReturn(new PageResponse<>(List.of(new ThirdPartyDownloadResponse(clue(), "", "", "")), 1, 1, 10, false));

        mockMvc.perform(get("/api/third-party-downloads/pending")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .cookie(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].clue.customerCode").value("XA0705-01"));
    }

    @Test
    void markDownloadedDelegatesToService() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user());
        when(thirdPartyDownloadService.markDownloaded(anyString(), nullable(String.class))).thenReturn(true);

        mockMvc.perform(post("/api/third-party-downloads/XA0705-01/mark-downloaded")
                        .cookie(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));

        verify(thirdPartyDownloadService).markDownloaded("XA0705-01", null);
    }

    @Test
    void failuresReturnsPagedFailureRows() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user());
        when(thirdPartyDownloadService.failurePage(
                isNull(), isNull(), isNull(), isNull(), any(), any(), nullable(String.class)
        )).thenReturn(new PageResponse<>(List.of(new ThirdPartyDownloadFailureRow(
                "XA0705-01", "wx123", "DOUYIN", "ACTIVE", "NEW", "小白", "XA", "", "", "小白", "XA", "下载失败", "2026-07-05 10:00"
        )), 1, 1, 10, false));

        mockMvc.perform(get("/api/third-party-downloads/failures")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .cookie(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].customerCode").value("XA0705-01"))
                .andExpect(jsonPath("$.data.records[0].remark").value("下载失败"));
    }

    private UserSession user() {
        return new UserSession("小白", "XA", "EMPLOYEE", "OPERATION", "", "HEADQUARTERS", null, null, false, List.of("THIRD_PARTY_POOL"));
    }

    private ClueResponse clue() {
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
                "NEW",
                "备注",
                List.of(),
                List.of(),
                false,
                "",
                1,
                null,
                null,
                "",
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
}
