package com.tourcrm.dto;

public record ClueCreateRequest(
        String contactInfo,
        String douyinImageUrl,
        String wechatImageUrl,
        String remark
) {
}

