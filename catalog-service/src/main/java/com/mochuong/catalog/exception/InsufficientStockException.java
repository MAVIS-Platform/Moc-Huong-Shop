package com.mochuong.catalog.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId, String sku, int requested, int available) {
        super("Insufficient stock for product " + sku + " (id=" + productId
                + "): requested=" + requested + ", available=" + available);
    }
}
