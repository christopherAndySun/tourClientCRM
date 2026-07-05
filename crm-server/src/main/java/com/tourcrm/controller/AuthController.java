package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.AdminUserUpdateRequest;
import com.tourcrm.dto.AuthLoginRequest;
import com.tourcrm.dto.AuthRegisterRequest;
import com.tourcrm.dto.AuthUserResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.UserRecord;
import com.tourcrm.dto.UserSession;
import com.tourcrm.dto.UserUpdateRequest;
import com.tourcrm.service.AuthService;
import com.tourcrm.service.AuthTokenSupport;
import com.tourcrm.service.SystemAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SystemAuditService systemAuditService;

    public AuthController(AuthService authService, SystemAuditService systemAuditService) {
        this.authService = authService;
        this.systemAuditService = systemAuditService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(
            @RequestBody AuthLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthUserResponse loginUser = authService.login(request);
        writeAuthCookie(servletRequest, servletResponse, loginUser.token(), authService.sessionExpirationSeconds());
        systemAuditService.recordUser(loginUser.name(), loginUser.employeeCode(), "LOGIN", "登录系统", "USER", loginUser.employeeCode(), "账号登录成功");
        return ApiResponse.ok(maskToken(loginUser));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        systemAuditService.record(token, "LOGOUT", "退出登录", "USER", "", "账号退出登录");
        authService.logout(token);
        writeAuthCookie(servletRequest, servletResponse, "", 0);
        return ApiResponse.ok(null);
    }

    @PostMapping("/register")
    public ApiResponse<Void> register() {
        return ApiResponse.fail("请联系管理员创建账号");
    }

    @GetMapping("/me")
    public ApiResponse<UserSession> me(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(authService.currentUser(token));
    }

    @PutMapping("/me")
    public ApiResponse<UserSession> updateMe(
            @RequestBody UserUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(authService.updateCurrentUser(request, token));
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserRecord>> users(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(authService.listUsersPage(page, pageSize, token));
    }

    @GetMapping("/leaders")
    public ApiResponse<List<UserRecord>> leaders(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(authService.listLeaders(token));
    }

    @GetMapping("/sales")
    public ApiResponse<List<UserRecord>> sales(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(authService.listSalesCandidates(token));
    }

    @PostMapping("/users")
    public ApiResponse<UserRecord> createUser(
            @RequestBody AuthRegisterRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        UserSession operator = authService.currentUser(token);
        UserRecord created = authService.createUser(request, token);
        systemAuditService.recordUser(operator.name(), operator.employeeCode(), "USER_CREATE", "新增账号", "USER", created.employeeCode(), "新增员工账号：" + created.name());
        return ApiResponse.ok(created);
    }

    @PutMapping("/users/{employeeCode}")
    public ApiResponse<UserRecord> updateUser(
            @PathVariable String employeeCode,
            @RequestBody AdminUserUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        UserSession operator = authService.currentUser(token);
        UserRecord updated = authService.updateUser(employeeCode, request, token);
        systemAuditService.recordUser(operator.name(), operator.employeeCode(), "USER_UPDATE", "修改账号", "USER", updated.employeeCode(), "修改员工账号：" + updated.name());
        return ApiResponse.ok(updated);
    }

    @DeleteMapping("/users/{employeeCode}")
    public ApiResponse<Boolean> deleteUser(
            @PathVariable String employeeCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        UserSession operator = authService.currentUser(token);
        Boolean deleted = authService.deleteUser(employeeCode, token);
        systemAuditService.recordUser(operator.name(), operator.employeeCode(), "USER_DELETE", "删除账号", "USER", employeeCode, "删除员工账号");
        return ApiResponse.ok(deleted);
    }

    private void writeAuthCookie(HttpServletRequest request, HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(AuthTokenSupport.COOKIE_NAME, token == null ? "" : token)
                .httpOnly(true)
                .secure(request != null && request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(Math.max(maxAgeSeconds, 0)))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private AuthUserResponse maskToken(AuthUserResponse user) {
        return new AuthUserResponse(
                "",
                user.name(),
                user.employeeCode(),
                user.role(),
                user.position(),
                user.leaderEmployeeCode(),
                user.orgType(),
                user.branchId(),
                user.branchName(),
                user.menuPermissions(),
                user.expiresAt()
        );
    }
}
