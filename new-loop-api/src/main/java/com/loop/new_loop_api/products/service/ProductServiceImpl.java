package com.loop.new_loop_api.products.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.products.dto.CreateProductRequest;
import com.loop.new_loop_api.products.dto.ProductResponse;
import com.loop.new_loop_api.products.dto.UpdateProductRequest;
import com.loop.new_loop_api.products.entity.Product;
import com.loop.new_loop_api.products.exception.ProductCodeAlreadyExistsException;
import com.loop.new_loop_api.products.exception.ProductNotFoundException;
import com.loop.new_loop_api.products.mapper.ProductMapper;
import com.loop.new_loop_api.products.repository.ProductRepository;
import com.loop.new_loop_api.products.service.iService.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper     productMapper;
    private final AuditService      auditService;

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsByCode(request.getCode())) {
            throw new ProductCodeAlreadyExistsException(request.getCode());
        }
        var product  = productMapper.toEntity(request);
        var response = productMapper.toResponse(productRepository.save(product));
        auditService.register("CREATE_PRODUCT", "Product", response.getId(), null, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        return productMapper.toResponse(findProductById(id));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        var product  = findProductById(id);
        var oldValue = productMapper.toResponse(product);
        productMapper.updateEntity(request, product);
        var response = productMapper.toResponse(productRepository.save(product));
        auditService.register("UPDATE_PRODUCT", "Product", id, oldValue, response);
        return response;
    }

    @Override
    @Transactional
    public void deactivateProduct(UUID id) {
        var product = findProductById(id);
        product.setActive(false);
        productRepository.save(product);
        auditService.register("DEACTIVATE_PRODUCT", "Product", id, null, null);
    }

    @Override
    @Transactional
    public void activateProduct(UUID id) {
        var product = findProductById(id);
        product.setActive(true);
        productRepository.save(product);
        auditService.register("ACTIVATE_PRODUCT", "Product", id, null, null);
    }

    private Product findProductById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
