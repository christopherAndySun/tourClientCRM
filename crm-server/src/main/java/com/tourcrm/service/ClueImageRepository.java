package com.tourcrm.service;

import com.tourcrm.dto.ImageFileDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ClueImageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;

    public ClueImageRepository(JdbcTemplate jdbcTemplate, FileStorageService fileStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
    }

    public void replaceImages(String customerCode, List<ImageFileDto> douyinImages, List<ImageFileDto> wechatImages) {
        List<String> oldImageUrls = readStoredImageUrls(customerCode);
        deleteImageRows(customerCode);
        List<String> activeImageUrls = new ArrayList<>();
        activeImageUrls.addAll(writeImages(customerCode, "DOUYIN", douyinImages));
        activeImageUrls.addAll(writeImages(customerCode, "WECHAT", wechatImages));
        oldImageUrls.removeAll(activeImageUrls);
        fileStorageService.deleteStoredFiles(oldImageUrls);
    }

    public void deleteImages(String customerCode) {
        List<String> imageUrls = readStoredImageUrls(customerCode);
        deleteImageRows(customerCode);
        fileStorageService.deleteStoredFiles(imageUrls);
    }

    public void deleteImageRows(String customerCode) {
        jdbcTemplate.update("DELETE FROM crm_clue_images WHERE customer_code = ?", customerCode);
    }

    public List<ImageFileDto> readImages(String customerCode, String imageType) {
        return jdbcTemplate.query("""
                        SELECT name, url, uid, sort_order, size_bytes, content_type
                        FROM crm_clue_images
                        WHERE customer_code = ? AND image_type = ?
                        ORDER BY sort_order, id
                        """,
                (rs, rowNum) -> new ImageFileDto(
                        rs.getString("name"),
                        rs.getString("url"),
                        rs.getString("uid"),
                        rs.getInt("sort_order"),
                        longOrNull(rs, "size_bytes"),
                        rs.getString("content_type")
                ),
                customerCode,
                imageType);
    }

    private List<String> writeImages(String customerCode, String imageType, List<ImageFileDto> images) {
        List<String> persistedUrls = new ArrayList<>();
        if (images == null) {
            return persistedUrls;
        }
        for (int i = 0; i < images.size(); i++) {
            ImageFileDto image = images.get(i);
            String imageUrl = fileStorageService.persistImageIfNeeded(customerCode, imageType, image, i);
            jdbcTemplate.update("""
                            INSERT INTO crm_clue_images (customer_code, image_type, name, url, uid, sort_order, size_bytes, content_type)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    customerCode, imageType, image.name(), imageUrl, image.uid(), image.sortOrder() == null ? i : image.sortOrder(),
                    imageSizeBytes(image), imageContentType(image, imageUrl));
            persistedUrls.add(imageUrl);
        }
        return persistedUrls;
    }

    private List<String> readStoredImageUrls(String customerCode) {
        return jdbcTemplate.query(
                "SELECT url FROM crm_clue_images WHERE customer_code = ? AND url LIKE '/uploads/%'",
                (rs, rowNum) -> rs.getString("url"),
                customerCode);
    }

    private Long imageSizeBytes(ImageFileDto image) {
        if (image == null) {
            return null;
        }
        if (image.sizeBytes() != null && image.sizeBytes() >= 0) {
            return image.sizeBytes();
        }
        String url = image.url();
        if (!StringUtils.hasText(url) || !url.startsWith("data:image/")) {
            return null;
        }
        int commaIndex = url.indexOf(',');
        if (commaIndex < 0) {
            return null;
        }
        try {
            return (long) java.util.Base64.getDecoder().decode(url.substring(commaIndex + 1)).length;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private String imageContentType(ImageFileDto image, String imageUrl) {
        if (image != null && StringUtils.hasText(image.contentType())) {
            return image.contentType();
        }
        String source = image != null && StringUtils.hasText(image.url()) ? image.url() : imageUrl;
        if (StringUtils.hasText(source) && source.startsWith("data:image/")) {
            int semicolonIndex = source.indexOf(';');
            return semicolonIndex > 5 ? source.substring(5, semicolonIndex) : null;
        }
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return null;
    }

    private Long longOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
