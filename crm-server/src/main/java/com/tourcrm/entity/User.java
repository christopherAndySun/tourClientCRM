package com.tourcrm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    private Long id;
    private String name;
    private String employeeCode;
    private String passwordHash;
    private String role;
    private String position;
    private Long groupId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

