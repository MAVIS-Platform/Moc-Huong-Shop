package com.mochuong.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        int stockQuantity,
        Long categoryId,
        String categoryName,
        Instant createdAt
) {
}
