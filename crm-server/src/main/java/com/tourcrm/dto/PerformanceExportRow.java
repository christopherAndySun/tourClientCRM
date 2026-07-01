package com.tourcrm.dto;

import com.alibaba.excel.annotation.ExcelProperty;

public class PerformanceExportRow {

    @ExcelProperty("员工")
    private final String employeeName;

    @ExcelProperty("员工编号")
    private final String employeeCode;

    @ExcelProperty("角色")
    private final String role;

    @ExcelProperty("岗位")
    private final String position;

    @ExcelProperty("直属组长")
    private final String leaderEmployeeCode;

    @ExcelProperty("总客资")
    private final long totalCount;

    @ExcelProperty("今日新增")
    private final long todayCount;

    @ExcelProperty("老客新需求")
    private final long repeatDemandCount;

    @ExcelProperty("已交定金")
    private final long dealedCount;

    @ExcelProperty("退单")
    private final long refundedCount;

    @ExcelProperty("已落地")
    private final long landedCount;

    @ExcelProperty("无效用户")
    private final long invalidCount;

    public PerformanceExportRow(PerformanceRowResponse row) {
        this.employeeName = row.employeeName();
        this.employeeCode = row.employeeCode();
        this.role = roleText(row.role());
        this.position = positionText(row.position());
        this.leaderEmployeeCode = row.leaderEmployeeCode();
        this.totalCount = row.totalCount();
        this.todayCount = row.todayCount();
        this.repeatDemandCount = row.repeatDemandCount();
        this.dealedCount = row.dealedCount();
        this.refundedCount = row.refundedCount();
        this.landedCount = row.landedCount();
        this.invalidCount = row.invalidCount();
    }

    private String roleText(String role) {
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "LEADER" -> "组长";
            case "EMPLOYEE" -> "员工";
            default -> role;
        };
    }

    private String positionText(String position) {
        return switch (position) {
            case "OPERATION" -> "运营";
            case "SALES" -> "销售";
            default -> position;
        };
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getRole() {
        return role;
    }

    public String getPosition() {
        return position;
    }

    public String getLeaderEmployeeCode() {
        return leaderEmployeeCode;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public long getTodayCount() {
        return todayCount;
    }

    public long getRepeatDemandCount() {
        return repeatDemandCount;
    }

    public long getDealedCount() {
        return dealedCount;
    }

    public long getRefundedCount() {
        return refundedCount;
    }

    public long getLandedCount() {
        return landedCount;
    }

    public long getInvalidCount() {
        return invalidCount;
    }
}
