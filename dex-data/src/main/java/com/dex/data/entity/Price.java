package com.dex.data.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Price {
    private Long id;
    private String pair;
    private BigDecimal price;
    private Long timestamp;
    private LocalDateTime createdAt;
}
