package com.mochuong.order.service;

import com.mochuong.order.client.CatalogFeignClient;
import com.mochuong.order.client.ProductSnapshotDto;
import com.mochuong.order.client.StockAdjustmentRequest;
import com.mochuong.order.exception.InsufficientStockException;
import com.mochuong.order.exception.ProductNotFoundException;
import com.mochuong.order.exception.ServiceDegradedException;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wraps OpenFeign calls to catalog-service with Resilience4j CircuitBreaker, Retry, and Bulkhead.
 */
@Service
public class CatalogIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(CatalogIntegrationService.class);

    private final CatalogFeignClient catalogFeignClient;

    public CatalogIntegrationService(CatalogFeignClient catalogFeignClient) {
        this.catalogFeignClient = catalogFeignClient;
    }

    @CircuitBreaker(name = "catalogService", fallbackMethod = "getProductFallback")
    @Retry(name = "catalogService")
    @Bulkhead(name = "catalogService")
    public ProductSnapshotDto fetchProduct(Long productId) {
        try {
            return catalogFeignClient.getProductById(productId);
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        }
    }

    @CircuitBreaker(name = "catalogService", fallbackMethod = "reserveStockFallback")
    public void reserveStock(Long productId, int quantity) {
        try {
            catalogFeignClient.reserveStock(productId, new StockAdjustmentRequest(quantity));
        } catch (FeignException.Conflict ex) {
            throw new InsufficientStockException(productId, quantity);
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        }
    }

    @CircuitBreaker(name = "catalogService", fallbackMethod = "releaseStockFallback")
    public void releaseStock(Long productId, int quantity) {
        try {
            catalogFeignClient.releaseStock(productId, new StockAdjustmentRequest(quantity));
        } catch (FeignException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        }
    }

    @SuppressWarnings("unused")
    private ProductSnapshotDto getProductFallback(Long productId, Throwable throwable) {
        if (throwable instanceof ProductNotFoundException pnf) {
            throw pnf;
        }
        log.warn("CircuitBreaker/Fallback triggered on fetchProduct({}): {}", productId, throwable.getMessage());
        throw new ServiceDegradedException("Catalog service is temporarily unavailable; please retry later", throwable);
    }

    @SuppressWarnings("unused")
    private void reserveStockFallback(Long productId, int quantity, Throwable throwable) {
        if (throwable instanceof InsufficientStockException ise) {
            throw ise;
        }
        if (throwable instanceof ProductNotFoundException pnf) {
            throw pnf;
        }
        log.warn("CircuitBreaker/Fallback triggered on reserveStock({}, {}): {}", productId, quantity, throwable.getMessage());
        throw new ServiceDegradedException("Catalog inventory service is temporarily unavailable", throwable);
    }

    @SuppressWarnings("unused")
    private void releaseStockFallback(Long productId, int quantity, Throwable throwable) {
        if (throwable instanceof ProductNotFoundException pnf) {
            throw pnf;
        }
        log.warn("CircuitBreaker/Fallback triggered on releaseStock({}, {}): {}", productId, quantity, throwable.getMessage());
        throw new ServiceDegradedException("Catalog inventory service is unavailable for stock release", throwable);
    }
}
