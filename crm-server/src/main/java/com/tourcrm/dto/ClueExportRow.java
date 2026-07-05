package com.tourcrm.dto;

import com.alibaba.excel.annotation.ExcelProperty;

public class ClueExportRow {
    @ExcelProperty("客户编号") private final String customerCode;
    @ExcelProperty("来源平台") private final String sourcePlatform;
    @ExcelProperty("添加方式") private final String addMethod;
    @ExcelProperty("联系方式") private final String contactInfo;
    @ExcelProperty("上传人") private final String uploader;
    @ExcelProperty("分配销售") private final String assignedSales;
    @ExcelProperty("分配销售工号") private final String assignedSalesEmployeeCode;
    @ExcelProperty("当前状态") private final String status;
    @ExcelProperty("定金金额") private final String depositAmount;
    @ExcelProperty("剩余尾款") private final String remainingBalance;
    @ExcelProperty("状态备注") private final String statusRemark;
    @ExcelProperty("退单金额") private final String refundAmount;
    @ExcelProperty("退款时间") private final String refundedAt;
    @ExcelProperty("落地时间") private final String landingAt;
    @ExcelProperty("落地备注") private final String landingRemark;
    @ExcelProperty("需求次数") private final Integer demandSequence;
    @ExcelProperty("是否老客新需求") private final String repeatDemand;
    @ExcelProperty("原客户编号") private final String originalCustomerCode;
    @ExcelProperty("备注") private final String remark;
    @ExcelProperty("抖音截图数") private final Integer douyinImageCount;
    @ExcelProperty("微信截图数") private final Integer wechatImageCount;
    @ExcelProperty("上传时间") private final String createdAt;
    @ExcelProperty("更新时间") private final String updatedAt;

    public ClueExportRow(ClueResponse clue) {
        this.customerCode = clue.customerCode();
        this.sourcePlatform = sourcePlatformText(clue.sourcePlatform());
        this.addMethod = addMethodText(clue.addMethod());
        this.contactInfo = clue.contactInfo();
        this.uploader = clue.uploader();
        this.assignedSales = clue.assignedSales();
        this.assignedSalesEmployeeCode = clue.assignedSalesEmployeeCode();
        this.status = statusText(clue.status());
        this.depositAmount = clue.depositAmount();
        this.remainingBalance = clue.remainingBalance();
        this.statusRemark = clue.statusRemark();
        this.refundAmount = clue.refundAmount();
        this.refundedAt = clue.refundedAt();
        this.landingAt = clue.landingAt();
        this.landingRemark = clue.landingRemark();
        this.demandSequence = clue.demandSequence() == null ? 1 : clue.demandSequence();
        this.repeatDemand = Boolean.TRUE.equals(clue.repeatDemand()) ? "是" : "否";
        this.originalCustomerCode = clue.originalCustomerCode();
        this.remark = clue.remark();
        this.douyinImageCount = clue.douyinImages() == null ? 0 : clue.douyinImages().size();
        this.wechatImageCount = clue.wechatImages() == null ? 0 : clue.wechatImages().size();
        this.createdAt = clue.createdAt();
        this.updatedAt = clue.updatedAt();
    }
    private String statusText(String status) { return switch (status) { case "NEW" -> "新录入"; case "FOLLOWING", "TO_DEAL" -> "跟进中"; case "PASSED" -> "已通过"; case "DEPOSIT_PAID", "DEALED" -> "已交定金"; case "INVALID" -> "无效用户"; case "REFUNDED" -> "退单"; case "LANDED" -> "已落地"; default -> status; }; }
    private String sourcePlatformText(String sourcePlatform) { return switch (sourcePlatform) { case "XIAOHONGSHU" -> "小红书"; default -> "抖音"; }; }
    private String addMethodText(String addMethod) { return switch (addMethod) { case "PASSIVE" -> "被动"; case "GUIDE" -> "领队"; default -> "主动"; }; }
    public String getCustomerCode() { return customerCode; }
    public String getSourcePlatform() { return sourcePlatform; }
    public String getAddMethod() { return addMethod; }
    public String getContactInfo() { return contactInfo; }
    public String getUploader() { return uploader; }
    public String getAssignedSales() { return assignedSales; }
    public String getAssignedSalesEmployeeCode() { return assignedSalesEmployeeCode; }
    public String getStatus() { return status; }
    public String getDepositAmount() { return depositAmount; }
    public String getRemainingBalance() { return remainingBalance; }
    public String getStatusRemark() { return statusRemark; }
    public String getRefundAmount() { return refundAmount; }
    public String getRefundedAt() { return refundedAt; }
    public String getLandingAt() { return landingAt; }
    public String getLandingRemark() { return landingRemark; }
    public Integer getDemandSequence() { return demandSequence; }
    public String getRepeatDemand() { return repeatDemand; }
    public String getOriginalCustomerCode() { return originalCustomerCode; }
    public String getRemark() { return remark; }
    public Integer getDouyinImageCount() { return douyinImageCount; }
    public Integer getWechatImageCount() { return wechatImageCount; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
