package com.tourcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.OcrRecognizeResponse;
import com.tourcrm.dto.SystemSettingsRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OcrService(ObjectMapper objectMapper, SystemSettingsService systemSettingsService) {
        this.objectMapper = objectMapper;
        this.systemSettingsService = systemSettingsService;
    }

    public OcrRecognizeResponse recognizeWechatId(String imageBase64) {
        String normalizedImage = normalizeImageBase64(imageBase64);
        if (!StringUtils.hasText(normalizedImage)) {
            throw new BusinessException("请先上传抖音截图第一张图片");
        }

        SystemSettingsRecord settings = systemSettingsService.getForSystem();
        if (!StringUtils.hasText(settings.ocrAppCode())) {
            throw new BusinessException("请先在系统设置中配置 OCR APP CODE");
        }

        String fullText = callOcr(settings.ocrAppCode(), normalizedImage);
        List<String> candidates = extractCandidates(fullText);
        String message = candidates.isEmpty() ? "未识别到微信号或手机号" : "识别成功";
        return new OcrRecognizeResponse(candidates, fullText, message);
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
            throw new BusinessException("OCR 服务调用失败，请稍后重试");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException("OCR 服务调用被中断，请重试");
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
        return value == null ? "" : value.replaceAll("^[：:：\\s]+|[，,。.;；:：\\s]+$", "").trim();
    }

    private String normalizeImageBase64(String imageBase64) {
        if (imageBase64 == null) {
            return "";
        }
        int commaIndex = imageBase64.indexOf(',');
        return commaIndex >= 0 ? imageBase64.substring(commaIndex + 1).trim() : imageBase64.trim();
    }

    private record OcrPayload(String image_base64) {
    }
}
