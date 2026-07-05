package com.tourcrm.dto;

import com.alibaba.excel.annotation.ExcelProperty;

public class DealExportRow {
    @ExcelProperty("成交编号") private final String dealCode;
    @ExcelProperty("客户编号") private final String customerCode;
    @ExcelProperty("客户姓名") private final String customerName;
    @ExcelProperty("定金/预付金") private final String deposit;
    @ExcelProperty("剩余尾款") private final String remainingBalance;
    @ExcelProperty("预定时间") private final String bookingDate;
    @ExcelProperty("加粉时间") private final String addWechatDate;
    @ExcelProperty("报价") private final String quoteText;
    @ExcelProperty("出行时间") private final String travelDate;
    @ExcelProperty("行程") private final String itinerary;
    @ExcelProperty("成交日期") private final String dealDate;
    @ExcelProperty("成交人") private final String dealUser;
    @ExcelProperty("成交人工号") private final String dealUserCode;
    @ExcelProperty("当日总计成单数") private final Integer totalDealSequence;
    @ExcelProperty("个人当日成单数") private final Integer personalDealSequence;
    @ExcelProperty("状态") private final String status;
    @ExcelProperty("退单金额") private final String refundAmount;
    @ExcelProperty("退单备注") private final String refundRemark;
    @ExcelProperty("退款时间") private final String refundedAt;
    @ExcelProperty("落地时间") private final String landingAt;
    @ExcelProperty("落地备注") private final String landingRemark;
    @ExcelProperty("登记时间") private final String createdAt;

    public DealExportRow(DealResponse deal) {
        this.dealCode = deal.dealCode();
        this.customerCode = deal.customerCode();
        this.customerName = deal.customerName();
        this.deposit = deal.deposit();
        this.remainingBalance = deal.remainingBalance();
        this.bookingDate = deal.bookingDate();
        this.addWechatDate = deal.addWechatDate();
        this.quoteText = deal.quoteText();
        this.travelDate = deal.travelDate();
        this.itinerary = deal.itinerary();
        this.dealDate = deal.dealDate();
        this.dealUser = deal.dealUser();
        this.dealUserCode = deal.dealUserCode();
        this.totalDealSequence = deal.totalDealSequence();
        this.personalDealSequence = deal.personalDealSequence();
        this.status = switch (deal.status()) { case "REFUNDED" -> "退单"; case "LANDED" -> "已落地"; default -> "已交定金"; };
        this.refundAmount = deal.refundAmount();
        this.refundRemark = deal.refundRemark();
        this.refundedAt = deal.refundedAt();
        this.landingAt = deal.landingAt();
        this.landingRemark = deal.landingRemark();
        this.createdAt = deal.createdAt();
    }
    public String getDealCode() { return dealCode; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerName() { return customerName; }
    public String getDeposit() { return deposit; }
    public String getRemainingBalance() { return remainingBalance; }
    public String getBookingDate() { return bookingDate; }
    public String getAddWechatDate() { return addWechatDate; }
    public String getQuoteText() { return quoteText; }
    public String getTravelDate() { return travelDate; }
    public String getItinerary() { return itinerary; }
    public String getDealDate() { return dealDate; }
    public String getDealUser() { return dealUser; }
    public String getDealUserCode() { return dealUserCode; }
    public Integer getTotalDealSequence() { return totalDealSequence; }
    public Integer getPersonalDealSequence() { return personalDealSequence; }
    public String getStatus() { return status; }
    public String getRefundAmount() { return refundAmount; }
    public String getRefundRemark() { return refundRemark; }
    public String getRefundedAt() { return refundedAt; }
    public String getLandingAt() { return landingAt; }
    public String getLandingRemark() { return landingRemark; }
    public String getCreatedAt() { return createdAt; }
}
