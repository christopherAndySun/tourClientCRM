package com.tourcrm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_group")
public class Group {
    private Long id;
    private String groupName;
    private Long leaderUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

