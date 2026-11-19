package com.mochuong.order.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long productId) {
        super("Product not found in catalog: " + productId);
    }
}
