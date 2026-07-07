package com.tourcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.OcrRecognizeResponse;
import com.tourcrm.dto.SystemSettingsRecord;
import com.tourcrm.dto.UserSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final String OCR_URL = "https://ocr-api.shiliuai.com/api/general_ocr/v1";
    private static final Pattern WECHAT_PATTERN = Pattern.compile("(?i)(?:微信号|微信|vx|wechat|weixin)\\s*[:：]?\\s*([a-z][-_a-z0-9]{5,19})");
    private static final Pattern GENERAL_WECHAT_PATTERN = Pattern.compile("(?i)\\b[a-z][-_a-z0-9]{5,19}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private final ObjectMapper objectMapper;
    private final SystemSettingsService systemSettingsService;
    private final FileStorageService fileStorageService;
    private final OcrCallLogRepository ocrCallLogRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OcrService(
            ObjectMapper objectMapper,
            SystemSettingsService systemSettingsService,
            FileStorageService fileStorageService,
            OcrCallLogRepository ocrCallLogRepository
    ) {
        this.objectMapper = objectMapper;
        this.systemSettingsService = systemSettingsService;
        this.fileStorageService = fileStorageService;
        this.ocrCallLogRepository = ocrCallLogRepository;
    }

    public OcrRecognizeResponse recognizeWechatId(String imageBase64) {
        return recognizeWechatId(imageBase64, "", null);
    }

    public OcrRecognizeResponse recognizeWechatId(String imageBase64, String imageUrl) {
        return recognizeWechatId(imageBase64, imageUrl, null);
    }

    public OcrRecognizeResponse recognizeWechatId(String imageBase64, String imageUrl, UserSession operator) {
        String normalizedUrl = imageUrl == null ? "" : imageUrl.trim();
        String normalizedBase64 = normalizeImageBase64(imageBase64);
        String imageKey = StringUtils.hasText(normalizedUrl) ? normalizedUrl : hash(normalizedBase64);
        if (StringUtils.hasText(imageKey)) {
            var cached = ocrCallLogRepository.findLatestByImageKey(imageKey);
            if (cached.isPresent()) {
                return cached.get().toResponse();
            }
        }

        String imagePayload = "";
        if (StringUtils.hasText(normalizedUrl)) {
            imagePayload = fileStorageService.readStoredImageAsBase64(normalizedUrl);
        }
        if (!StringUtils.hasText(imagePayload)) {
            imagePayload = normalizedBase64;
        }
        if (!StringUtils.hasText(imagePayload)) {
            throw new BusinessException("请先上传抖音截图第一张图片");
        }

        SystemSettingsRecord settings = systemSettingsService.getForSystem();
        if (!StringUtils.hasText(settings.ocrAppCode())) {
            throw new BusinessException("请先在系统设置中配置 OCR APP CODE");
        }

        try {
            String fullText = callOcr(settings.ocrAppCode(), imagePayload);
            List<String> candidates = extractCandidates(fullText);
            String status = candidates.isEmpty() ? "NO_MATCH" : "SUCCESS";
            String message = candidates.isEmpty() ? "未识别到微信号或手机号" : "识别成功";
            record(imageKey, normalizedUrl, status, candidates, fullText, "", operator);
            return new OcrRecognizeResponse(candidates, fullText, message);
        } catch (BusinessException error) {
            record(imageKey, normalizedUrl, "FAILED", List.of(), "", error.getMessage(), operator);
            throw error;
        }
    }

    private String callOcr(String appCode, String imageBase64) {
        try {
            String requestBody = objectMapper.writeValueAsString(new OcrPayload(imageBase64));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OCR_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "APPCODE " + appCode.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("OCR 服务请求失败，状态码：" + response.statusCode());
            }
            return parseText(response.body());
        } catch (IOException error) {
            throw new BusinessException("OCR 服务调用失败，请手动填写联系方式");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException("OCR 服务调用被中断，请手动填写联系方式");
        }
    }

    private String parseText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("data").path("content");
        if (!content.isArray()) {
            String msg = root.path("msg").asText(root.path("message").asText(""));
            if (StringUtils.hasText(msg)) {
                throw new BusinessException("OCR 识别失败：" + msg);
            }
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode item : content) {
            String text = item.path("text").asText("");
            if (StringUtils.hasText(text)) {
                builder.append(text).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private List<String> extractCandidates(String text) {
        Set<String> values = new LinkedHashSet<>();
        collectMatches(WECHAT_PATTERN, text, values, true);
        collectMatches(PHONE_PATTERN, text, values, false);
        if (values.isEmpty()) {
            collectMatches(GENERAL_WECHAT_PATTERN, text, values, false);
        }
        return values.stream().limit(8).toList();
    }

    private void collectMatches(Pattern pattern, String text, Set<String> values, boolean firstGroup) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = firstGroup ? matcher.group(1) : matcher.group();
            value = cleanCandidate(value);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
    }

    private String cleanCandidate(String value) {
        return value == null ? "" : value.replaceAll("^[：:\\s]+|[，。;；：:\\s]+$", "").trim();
    }

    private String normalizeImageBase64(String imageBase64) {
        if (imageBase64 == null) {
            return "";
        }
        int commaIndex = imageBase64.indexOf(',');
        return commaIndex >= 0 ? imageBase64.substring(commaIndex + 1).trim() : imageBase64.trim();
    }

    private void record(String imageKey, String imageUrl, String status, List<String> candidates, String fullText, String errorMessage, UserSession operator) {
        if (!StringUtils.hasText(imageKey)) {
            return;
        }
        ocrCallLogRepository.record(
                imageKey,
                imageUrl,
                status,
                candidates,
                fullText,
                errorMessage,
                operator == null ? null : operator.employeeCode(),
                operator == null ? null : operator.name()
        );
    }

    private String hash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "base64:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            return "base64:" + value.length();
        }
    }

    private record OcrPayload(String image_base64) {
    }
}
