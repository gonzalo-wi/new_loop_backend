package com.loop.new_loop_api.products.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.products.dto.CreateProductRequest;
import com.loop.new_loop_api.products.dto.UpdateProductRequest;
import com.loop.new_loop_api.products.entity.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private static final String BASE_CREATE_JSON =
            "\"code\":\"PM\",\"name\":\"PalMad\",\"displayOrder\":1,\"type\":\"RETORNABLE\",\"unit\":\"UN\"";

    private final ProductMapper  productMapper = new ProductMapper();
    private final ObjectMapper   objectMapper  = new ObjectMapper();

    @Test
    void should_markProductAsFullEqualsTotal_when_createRequestSetsFlag() throws Exception {
        var request = objectMapper.readValue("{" + BASE_CREATE_JSON + ",\"fullEqualsTotal\":true}", CreateProductRequest.class);

        var product = productMapper.toEntity(request);

        assertThat(product.getFullEqualsTotal()).isTrue();
        assertThat(productMapper.toResponse(product).getFullEqualsTotal()).isTrue();
    }

    @Test
    void should_defaultFullEqualsTotalToFalse_when_createRequestOmitsFlag() throws Exception {
        var request = objectMapper.readValue("{" + BASE_CREATE_JSON + "}", CreateProductRequest.class);

        assertThat(productMapper.toEntity(request).getFullEqualsTotal()).isFalse();
    }

    @Test
    void should_updateFullEqualsTotal_when_updateRequestSetsFlag() throws Exception {
        var product = Product.builder().code("ZR").fullEqualsTotal(true).build();
        var request = objectMapper.readValue("{\"fullEqualsTotal\":false}", UpdateProductRequest.class);

        productMapper.updateEntity(request, product);

        assertThat(product.getFullEqualsTotal()).isFalse();
    }

    @Test
    void should_keepFullEqualsTotal_when_updateRequestOmitsFlag() throws Exception {
        var product = Product.builder().code("ZR").fullEqualsTotal(true).build();
        var request = objectMapper.readValue("{\"name\":\"Rack\"}", UpdateProductRequest.class);

        productMapper.updateEntity(request, product);

        assertThat(product.getFullEqualsTotal()).isTrue();
    }
}
