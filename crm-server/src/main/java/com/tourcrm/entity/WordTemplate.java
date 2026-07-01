package com.tourcrm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("word_template")
public class WordTemplate {
    private Long id;
    private String templateName;
    private String templateFilePath;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

