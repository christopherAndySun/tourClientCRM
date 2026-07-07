package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.CustomerHistoryResponse;
import com.tourcrm.service.CustomerClueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerProfileController {

    private final CustomerClueService customerClueService;

    public CustomerProfileController(CustomerClueService customerClueService) {
        this.customerClueService = customerClueService;
    }

    @GetMapping("/{rootCustomerCode}")
    public ApiResponse<CustomerHistoryResponse> detail(
            @PathVariable String rootCustomerCode,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return ApiResponse.ok(customerClueService.customerProfile(rootCustomerCode, token));
    }
}
