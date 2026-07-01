package com.tourcrm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("customer_clue")
public class CustomerClue {
    private Long id;
    private String customerCode;
    private String contactInfo;
    private String douyinImageUrl;
    private String wechatImageUrl;
    private String remark;
    private String status;
    private Long uploaderUserId;
    private Long currentOwnerUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}

