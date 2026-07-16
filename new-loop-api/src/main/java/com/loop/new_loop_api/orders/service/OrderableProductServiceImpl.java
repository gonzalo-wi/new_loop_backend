package com.loop.new_loop_api.orders.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.service.ActivationService;
import com.loop.new_loop_api.orders.dto.CreateOrderableProductRequest;
import com.loop.new_loop_api.orders.dto.OrderableProductResponse;
import com.loop.new_loop_api.orders.dto.UpdateOrderableProductRequest;
import com.loop.new_loop_api.orders.entity.OrderableProduct;
import com.loop.new_loop_api.orders.exception.OrderableProductNotFoundException;
import com.loop.new_loop_api.orders.mapper.OrderableProductMapper;
import com.loop.new_loop_api.orders.repository.OrderableProductRepository;
import com.loop.new_loop_api.orders.service.iService.OrderableProductService;
import com.loop.new_loop_api.products.exception.ProductCodeAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderableProductServiceImpl implements OrderableProductService {

    private final OrderableProductRepository orderableProductRepository;
    private final OrderableProductMapper     orderableProductMapper;
    private final AuditService               auditService;
    private final ActivationService          activationService;

    @Override
    @Transactional
    public OrderableProductResponse createProduct(CreateOrderableProductRequest request) {
        validateCodeIsAvailable(request.getCode());
        var product  = orderableProductMapper.toEntity(request);
        var saved    = orderableProductRepository.save(product);
        var response = orderableProductMapper.toResponse(saved);
        auditService.register("CREATE_ORDERABLE_PRODUCT", "OrderableProduct", saved.getId(), null, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderableProductResponse> getAllProducts(Boolean active, Pageable pageable) {
        var page = active != null
                ? orderableProductRepository.findAllByActive(active, pageable)
                : orderableProductRepository.findAll(pageable);
        return page.map(orderableProductMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderableProductResponse getProductById(UUID id) {
        return orderableProductMapper.toResponse(findProductById(id));
    }

    @Override
    @Transactional
    public OrderableProductResponse updateProduct(UUID id, UpdateOrderableProductRequest request) {
        var product  = findProductById(id);
        var oldValue = orderableProductMapper.toResponse(product);
        orderableProductMapper.updateEntity(request, product);
        var response = orderableProductMapper.toResponse(orderableProductRepository.save(product));
        auditService.register("UPDATE_ORDERABLE_PRODUCT", "OrderableProduct", id, oldValue, response);
        return response;
    }

    @Override
    @Transactional
    public void deactivateProduct(UUID id) {
        activationService.setActive(orderableProductRepository, findProductById(id), id, false,
                "OrderableProduct", "DEACTIVATE_ORDERABLE_PRODUCT");
    }

    @Override
    @Transactional
    public void activateProduct(UUID id) {
        activationService.setActive(orderableProductRepository, findProductById(id), id, true,
                "OrderableProduct", "ACTIVATE_ORDERABLE_PRODUCT");
    }

    private void validateCodeIsAvailable(String code) {
        if (orderableProductRepository.existsByCode(code)) {
            throw new ProductCodeAlreadyExistsException(code);
        }
    }

    private OrderableProduct findProductById(UUID id) {
        return orderableProductRepository.findById(id)
                .orElseThrow(() -> new OrderableProductNotFoundException(id));
    }
}
