package com.tourcrm.service;

import com.tourcrm.dto.ImageFileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
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
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("图片不能超过 10MB");
        }
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

    public ImageFileDto storeUploadedImage(MultipartFile file, String imageType, Integer sortOrder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("图片不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalStateException("只能上传图片文件");
        }
        String extension = cleanImageExtension(contentType.substring("image/".length()));
        String safeType = StringUtils.hasText(imageType) ? imageType.replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(Locale.ROOT) : "image";
        String uid = UUID.randomUUID().toString().replace("-", "");
        String fileName = safeType + "-" + System.currentTimeMillis() + "-" + uid + "." + extension;
        Path relativeDir = Path.of("clue-images", "pending");
        Path targetDir = uploadDir.resolve(relativeDir).normalize();
        Path target = targetDir.resolve(fileName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalStateException("图片存储路径不合法");
        }
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
            String url = "/uploads/" + relativeDir.resolve(fileName).toString().replace("\\", "/");
            return new ImageFileDto(file.getOriginalFilename(), url, uid, sortOrder, file.getSize(), contentType);
        } catch (IOException error) {
            throw new IllegalStateException("保存图片文件失败", error);
        }
    }

    public void deleteStoredFiles(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        for (String url : urls) {
            deleteStoredFile(url);
        }
    }

    public String readStoredImageAsBase64(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("/uploads/")) {
            return "";
        }
        byte[] bytes = readStoredFileBytes(url);
        if (bytes.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] readStoredFileBytes(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("/uploads/")) {
            return new byte[0];
        }
        Path target = uploadDir.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(uploadDir) || !Files.exists(target)) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException error) {
            return new byte[0];
        }
    }

    private void deleteStoredFile(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("/uploads/")) {
            return;
        }
        Path target = uploadDir.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(uploadDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best effort cleanup. Database state remains the source of truth.
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
