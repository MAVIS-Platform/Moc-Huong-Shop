package com.mochuong.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mochuong.catalog.exception.InsufficientStockException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

    private Product sampleProduct(int initialStock) {
        Category category = new Category("NHANG-TRAM", "Nhang Tram Huong", "Nhang tram tu nhien");
        return new Product(
                "MH-NHANG-01",
                "Nhang Tram Khong Tam Cao Cap",
                "Hop 100 que",
                new BigDecimal("350000.00"),
                initialStock,
                category
        );
    }

    @Test
    void reserveStock_sufficientStock_decrementsQuantity() {
        Product product = sampleProduct(10);

        product.reserveStock(4);

        assertEquals(6, product.getStockQuantity());
    }

    @Test
    void reserveStock_insufficientStock_throwsInsufficientStockException() {
        Product product = sampleProduct(3);

        assertThrows(InsufficientStockException.class, () -> product.reserveStock(5));
        assertEquals(3, product.getStockQuantity());
    }

    @Test
    void releaseStock_validQuantity_incrementsQuantity() {
        Product product = sampleProduct(5);

        product.releaseStock(3);

        assertEquals(8, product.getStockQuantity());
    }

    @Test
    void reserveOrRelease_nonPositiveQuantity_throwsIllegalArgumentException() {
        Product product = sampleProduct(5);

        assertThrows(IllegalArgumentException.class, () -> product.reserveStock(0));
        assertThrows(IllegalArgumentException.class, () -> product.releaseStock(-1));
    }
}
