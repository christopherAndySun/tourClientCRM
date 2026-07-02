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

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(@RequestBody AuthLoginRequest request) {
        return ApiResponse.ok(authService.login(request));
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
        return ApiResponse.ok(authService.createUser(request, token));
    }

    @PutMapping("/users/{employeeCode}")
    public ApiResponse<UserRecord> updateUser(
            @PathVariable String employeeCode,
            @RequestBody AdminUserUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(authService.updateUser(employeeCode, request, token));
    }

    @DeleteMapping("/users/{employeeCode}")
    public ApiResponse<Boolean> deleteUser(
            @PathVariable String employeeCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(authService.deleteUser(employeeCode, token));
    }
}
