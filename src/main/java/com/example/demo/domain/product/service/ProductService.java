package com.example.demo.domain.product.service;

import com.example.demo.domain.product.dto.ProductResponseDto;
import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // 1. 전체 상품 목록 조회
    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponseDto::from)
                .toList();
    }

    // 2. 단일 상품 상세 조회
    public ProductResponseDto getProductById(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 ID입니다: " + productId));
        return ProductResponseDto.from(product);
    }
}