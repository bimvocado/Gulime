package com.example.demo.domain.product.repository;

import com.example.demo.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    @Override
    @EntityGraph(attributePaths = "conditions")
    java.util.List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = "conditions")
    java.util.Optional<Product> findById(String productId);
}