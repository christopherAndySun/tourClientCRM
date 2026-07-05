package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ImageFileDto;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ClueWordService {

    private static final int MAX_IMAGE_WIDTH = 460;
    private static final int MAX_IMAGE_HEIGHT = 650;

    private final CustomerClueService customerClueService;
    private final FileStorageService fileStorageService;
    private final SystemAuditService systemAuditService;

    public ClueWordService(CustomerClueService customerClueService, FileStorageService fileStorageService, SystemAuditService systemAuditService) {
        this.customerClueService = customerClueService;
        this.fileStorageService = fileStorageService;
        this.systemAuditService = systemAuditService;
    }

    public WordFile generate(String customerCode, String token) {
        ClueResponse clue = customerClueService.findByCustomerCode(customerCode, token)
                .orElseThrow(() -> new BusinessException("客户线索不存在"));
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph contactParagraph = document.createParagraph();
            XWPFRun contactRun = contactParagraph.createRun();
            contactRun.setText(StringUtils.hasText(clue.contactInfo()) ? clue.contactInfo() : "待补充");
            contactRun.setFontSize(10);
            addBlankLines(document, 2);

            for (ImageFileDto image : orderedImages(clue)) {
                addImage(document, image);
                addBlankLines(document, 2);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            String fileName = safeFilename(clue.customerCode()) + ".docx";
            systemAuditService.record(token, "CLUE_WORD_DOWNLOAD", "下载客户 Word", "CLUE", clue.customerCode(), "下载客户线索 Word 文档");
            return new WordFile(fileName, output.toByteArray());
        } catch (IOException error) {
            throw new BusinessException("生成 Word 文档失败");
        }
    }

    private List<ImageFileDto> orderedImages(ClueResponse clue) {
        List<ImageFileDto> images = new ArrayList<>();
        images.addAll(sorted(clue.douyinImages()));
        images.addAll(sorted(clue.wechatImages()));
        return images.stream().filter(image -> image != null && StringUtils.hasText(image.url())).toList();
    }

    private List<ImageFileDto> sorted(List<ImageFileDto> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .sorted(Comparator.comparing(image -> image.sortOrder() == null ? 0 : image.sortOrder()))
                .toList();
    }

    private void addImage(XWPFDocument document, ImageFileDto image) {
        byte[] bytes = fileStorageService.readStoredFileBytes(image.url());
        if (bytes.length == 0) {
            addTextLine(document, "图片文件不存在：" + image.url());
            return;
        }
        int pictureType = pictureType(image.url(), image.contentType());
        if (pictureType == -1) {
            addTextLine(document, "暂不支持该图片格式：" + image.url());
            return;
        }
        Dimension dimension = imageSize(bytes);
        Dimension target = fitImage(dimension);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun run = paragraph.createRun();
            run.addPicture(
                    input,
                    pictureType,
                    StringUtils.hasText(image.name()) ? image.name() : "客户截图",
                    Units.toEMU(target.width),
                    Units.toEMU(target.height)
            );
        } catch (Exception error) {
            addTextLine(document, "图片写入 Word 失败：" + image.url());
        }
    }

    private Dimension imageSize(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException ignored) {
            // 读取失败时走默认尺寸，避免单张异常图片阻断整份文档。
        }
        return new Dimension(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
    }

    private Dimension fitImage(Dimension dimension) {
        double scale = Math.min(1D, Math.min(MAX_IMAGE_WIDTH / (double) dimension.width, MAX_IMAGE_HEIGHT / (double) dimension.height));
        return new Dimension(Math.max(1, (int) Math.round(dimension.width * scale)), Math.max(1, (int) Math.round(dimension.height * scale)));
    }

    private int pictureType(String url, String contentType) {
        String normalized = ((StringUtils.hasText(contentType) ? contentType : url) == null ? "" : (StringUtils.hasText(contentType) ? contentType : url)).toLowerCase(Locale.ROOT);
        if (normalized.contains("png") || normalized.endsWith(".png")) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        if (normalized.contains("gif") || normalized.endsWith(".gif")) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        if (normalized.contains("bmp") || normalized.endsWith(".bmp")) {
            return XWPFDocument.PICTURE_TYPE_BMP;
        }
        if (normalized.contains("jpg") || normalized.contains("jpeg") || normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return XWPFDocument.PICTURE_TYPE_JPEG;
        }
        return -1;
    }

    private void addBlankLines(XWPFDocument document, int count) {
        for (int i = 0; i < count; i++) {
            document.createParagraph();
        }
    }

    private void addTextLine(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(10);
    }

    private String safeFilename(String value) {
        String source = StringUtils.hasText(value) ? value : "客户线索";
        return source.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public record WordFile(String fileName, byte[] bytes) {
    }
}
