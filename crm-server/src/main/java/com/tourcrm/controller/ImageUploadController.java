package com.tourcrm.controller;

import com.tourcrm.common.ApiResponse;
import com.tourcrm.dto.ImageFileDto;
import com.tourcrm.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class ImageUploadController {

    private final FileStorageService fileStorageService;

    public ImageUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/images")
    public ApiResponse<ImageFileDto> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String imageType,
            @RequestParam(required = false) Integer sortOrder
    ) {
        return ApiResponse.ok(fileStorageService.storeUploadedImage(file, imageType, sortOrder));
    }
}
