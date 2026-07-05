package com.tourcrm.dto;

import com.alibaba.excel.annotation.ExcelProperty;

public class ThirdPartyDownloadFailureExportRow {
    @ExcelProperty("客户编号") private final String customerCode;
    @ExcelProperty("客户联系方式") private final String contactInfo;
    @ExcelProperty("来源平台") private final String sourcePlatform;
    @ExcelProperty("添加方式") private final String addMethod;
    @ExcelProperty("当前状态") private final String status;
    @ExcelProperty("上传运营") private final String uploader;
    @ExcelProperty("运营编号") private final String uploaderEmployeeCode;
    @ExcelProperty("分配销售") private final String assignedSales;
    @ExcelProperty("销售编号") private final String assignedSalesEmployeeCode;
    @ExcelProperty("操作人") private final String operator;
    @ExcelProperty("操作人编号") private final String operatorCode;
    @ExcelProperty("失败原因") private final String remark;
    @ExcelProperty("失败时间") private final String failedAt;

    public ThirdPartyDownloadFailureExportRow(ThirdPartyDownloadFailureRow row) {
        this.customerCode = row.customerCode();
        this.contactInfo = row.contactInfo();
        this.sourcePlatform = row.sourcePlatform();
        this.addMethod = row.addMethod();
        this.status = row.status();
        this.uploader = row.uploader();
        this.uploaderEmployeeCode = row.uploaderEmployeeCode();
        this.assignedSales = row.assignedSales();
        this.assignedSalesEmployeeCode = row.assignedSalesEmployeeCode();
        this.operator = row.operator();
        this.operatorCode = row.operatorCode();
        this.remark = row.remark();
        this.failedAt = row.failedAt();
    }

    public String getCustomerCode() { return customerCode; }
    public String getContactInfo() { return contactInfo; }
    public String getSourcePlatform() { return sourcePlatform; }
    public String getAddMethod() { return addMethod; }
    public String getStatus() { return status; }
    public String getUploader() { return uploader; }
    public String getUploaderEmployeeCode() { return uploaderEmployeeCode; }
    public String getAssignedSales() { return assignedSales; }
    public String getAssignedSalesEmployeeCode() { return assignedSalesEmployeeCode; }
    public String getOperator() { return operator; }
    public String getOperatorCode() { return operatorCode; }
    public String getRemark() { return remark; }
    public String getFailedAt() { return failedAt; }
}
