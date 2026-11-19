package com.mochuong.order.client;

import java.math.BigDecimal;

public record ProductSnapshotDto(
        Long id,
        String sku,
        String name,
        BigDecimal unitPrice,
        int stockQuantity
) {
}
