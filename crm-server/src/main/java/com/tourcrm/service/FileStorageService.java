package com.tourcrm.service;

import com.tourcrm.dto.ImageFileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String persistImageIfNeeded(String customerCode, String imageType, ImageFileDto image, int index) {
        String url = image == null ? "" : image.url();
        if (!StringUtils.hasText(url) || !url.startsWith("data:image/")) {
            return url;
        }
        int commaIndex = url.indexOf(',');
        int slashIndex = url.indexOf('/');
        int semicolonIndex = url.indexOf(';');
        if (commaIndex < 0 || slashIndex < 0 || semicolonIndex < slashIndex) {
            return url;
        }

        String extension = cleanImageExtension(url.substring(slashIndex + 1, semicolonIndex));
        byte[] bytes = java.util.Base64.getDecoder().decode(url.substring(commaIndex + 1));
        String safeCustomerCode = customerCode.replaceAll("[^A-Za-z0-9_-]", "_");
        String safeType = imageType.replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase();
        String uidPart = StringUtils.hasText(image.uid()) ? image.uid() : String.valueOf(index);
        String fileName = safeCustomerCode + "-" + safeType + "-" + System.currentTimeMillis() + "-" + uidPart.replaceAll("[^A-Za-z0-9_-]", "_") + "." + extension;
        Path relativeDir = Path.of("clue-images", safeCustomerCode);
        Path targetDir = uploadDir.resolve(relativeDir).normalize();
        Path target = targetDir.resolve(fileName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalStateException("图片存储路径不合法");
        }
        try {
            Files.createDirectories(targetDir);
            Files.write(target, bytes);
            return "/uploads/" + relativeDir.resolve(fileName).toString().replace("\\", "/");
        } catch (IOException error) {
            throw new IllegalStateException("保存图片文件失败", error);
        }
    }

    private String cleanImageExtension(String extension) {
        String normalized = extension == null ? "jpg" : extension.toLowerCase();
        return switch (normalized) {
            case "jpeg", "jpg" -> "jpg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "webp" -> "webp";
            default -> "jpg";
        };
    }
}
