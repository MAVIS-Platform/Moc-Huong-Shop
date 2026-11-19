package com.mochuong.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "catalog-service", path = "/api/v1/products")
public interface CatalogFeignClient {

    @GetMapping("/{id}")
    ProductSnapshotDto getProductById(@PathVariable("id") Long id);

    @PostMapping("/{id}/reserve")
    void reserveStock(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequest request);

    @PostMapping("/{id}/release")
    void releaseStock(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequest request);
}
