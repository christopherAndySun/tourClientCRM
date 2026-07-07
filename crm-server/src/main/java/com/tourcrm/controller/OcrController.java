package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.OcrRecognizeRequest;
import com.tourcrm.dto.OcrRecognizeResponse;
import com.tourcrm.dto.UserSession;
import com.tourcrm.service.AuthService;
import com.tourcrm.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final AuthService authService;
    private final OcrService ocrService;

    public OcrController(AuthService authService, OcrService ocrService) {
        this.authService = authService;
        this.ocrService = ocrService;
    }

    @PostMapping("/wechat-id")
    public ApiResponse<OcrRecognizeResponse> recognizeWechatId(
            @RequestBody OcrRecognizeRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        UserSession currentUser = authService.currentUser(token);
        return ApiResponse.ok(ocrService.recognizeWechatId(
                request == null ? "" : request.imageBase64(),
                request == null ? "" : request.imageUrl(),
                currentUser
        ));
    }
}
