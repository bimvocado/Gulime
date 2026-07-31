package com.example.demo;

import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.service.ProductCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoApplicationTests {

    @Autowired
    private ProductCatalog productCatalog;

    @Test
    void contextLoads() {
        assertThat(productCatalog.findAll()).isNotEmpty();
    }

    @Test
    void validationSetIsConvertedToEngineDomain() {
        SavingsProduct product = productCatalog.findAll().stream()
                .filter(item -> item.productName().equals("e-그린세이브예금"))
                .findFirst()
                .orElseThrow();

        assertThat(product.baseRate()).isEqualTo(0.0355);
        assertThat(product.maxRate()).isEqualTo(0.0385);
        assertThat(product.conditions())
                .extracting(condition -> condition.type())
                .contains(
                        ConditionType.DEPOSIT_AMOUNT,
                        ConditionType.BALANCE_MAINTENANCE,
                        ConditionType.FIRST_TRADE
                );
    }
}
