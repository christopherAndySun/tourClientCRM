package com.tourcrm.dto;

import java.util.List;

public record ClueSaveRequest(
        String sourcePlatform,
        String addMethod,
        String contactInfo,
        Boolean hasWechatId,
        String status,
        String remark,
        List<ImageFileDto> douyinImages,
        List<ImageFileDto> wechatImages,
        Boolean allowRepeatDemand,
        String depositAmount,
        String remainingBalance,
        String statusRemark,
        String refundAmount,
        String refundedAt,
        String landingAt,
        String landingRemark
) {
}
