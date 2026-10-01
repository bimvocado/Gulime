package com.example.demo.savings.service;

import com.example.demo.savings.domain.SavingsProduct;

import java.util.List;
import java.util.Optional;

/**
 * 계산 엔진이 상품 저장 방식(JSON, JPA 등)을 알지 않도록 하는 조회 경계입니다.
 */
public interface ProductCatalog {

    Optional<SavingsProduct> findById(String productId);

    List<SavingsProduct> findAll();
}
