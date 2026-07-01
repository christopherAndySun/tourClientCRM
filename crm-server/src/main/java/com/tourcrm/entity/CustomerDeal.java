package com.tourcrm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("customer_deal")
public class CustomerDeal {
    private Long id;
    private Long customerClueId;
    private Long salesUserId;
    private String customerName;
    private String deposit;
    private LocalDate bookingDate;
    private LocalDate addWechatDate;
    private String quoteText;
    private String travelDate;
    private String itinerary;
    private LocalDate dealDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

