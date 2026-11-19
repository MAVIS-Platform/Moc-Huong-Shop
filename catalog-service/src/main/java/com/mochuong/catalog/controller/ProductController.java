package com.mochuong.catalog.controller;

import com.mochuong.catalog.dto.CreateProductRequest;
import com.mochuong.catalog.dto.ProductResponse;
import com.mochuong.catalog.dto.StockAdjustmentRequest;
import com.mochuong.catalog.exception.ForbiddenOperationException;
import com.mochuong.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> listProducts(
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.listProducts(categoryId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody CreateProductRequest request) {
        if (!"ADMIN".equalsIgnoreCase(userRole) && !"STAFF".equalsIgnoreCase(userRole)) {
            throw new ForbiddenOperationException("Only ADMIN or STAFF can create catalog products");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<ProductResponse> reserveStock(
            @PathVariable("id") Long id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(productService.reserveStock(id, request.quantity()));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<ProductResponse> releaseStock(
            @PathVariable("id") Long id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(productService.releaseStock(id, request.quantity()));
    }
}
