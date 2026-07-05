package com.tourcrm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.config.ApiAuthenticationFilter;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.UserSession;
import com.tourcrm.service.AuthService;
import com.tourcrm.service.AuthTokenSupport;
import com.tourcrm.service.CustomerClueService;
import com.tourcrm.service.SystemAuditService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerClueControllerApiTest {

    private final CustomerClueService customerClueService = mock(CustomerClueService.class);
    private final SystemAuditService systemAuditService = mock(SystemAuditService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CustomerClueController(customerClueService, systemAuditService))
            .addFilters(new ApiAuthenticationFilter(authService, new ObjectMapper()))
            .build();

    @Test
    void listRejectsUnauthenticatedRequestBeforeController() throws Exception {
        when(authService.currentUser("")).thenThrow(new BusinessException("请先登录"));

        mockMvc.perform(get("/api/clues"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void listReturnsPagedRowsWhenAuthenticated() throws Exception {
        when(authService.currentUser("cookie-token")).thenReturn(user());
        when(customerClueService.listPage(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(), any(), nullable(String.class)
        )).thenReturn(new PageResponse<>(List.of(clue()), 1, 1, 10, false));

        mockMvc.perform(get("/api/clues")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .cookie(new Cookie(AuthTokenSupport.COOKIE_NAME, "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].customerCode").value("XA0705-01"));
    }

    private UserSession user() {
        return new UserSession("小白", "XA", "EMPLOYEE", "OPERATION", "", "HEADQUARTERS", null, null, List.of("CLUES"));
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
