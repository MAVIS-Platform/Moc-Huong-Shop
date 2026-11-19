package com.mochuong.catalog.service;

import com.mochuong.catalog.domain.Category;
import com.mochuong.catalog.domain.Product;
import com.mochuong.catalog.dto.CreateProductRequest;
import com.mochuong.catalog.dto.ProductResponse;
import com.mochuong.catalog.exception.CategoryNotFoundException;
import com.mochuong.catalog.exception.DuplicateSkuException;
import com.mochuong.catalog.exception.ProductNotFoundException;
import com.mochuong.catalog.repository.CategoryRepository;
import com.mochuong.catalog.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Long categoryId, Pageable pageable) {
        Page<Product> page = (categoryId != null)
                ? productRepository.findByCategoryId(categoryId, pageable)
                : productRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        String normalizedSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new DuplicateSkuException(normalizedSku);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        Product product = new Product(
                normalizedSku,
                request.name().trim(),
                request.description(),
                request.unitPrice(),
                request.stockQuantity(),
                category
        );
        return toResponse(productRepository.save(product));
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public ProductResponse reserveStock(Long id, int quantity) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.reserveStock(quantity);
        return toResponse(productRepository.save(product));
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public ProductResponse releaseStock(Long id, int quantity) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.releaseStock(quantity);
        return toResponse(productRepository.save(product));
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                product.getStockQuantity(),
                category.getId(),
                category.getName(),
                product.getCreatedAt()
        );
    }
}
