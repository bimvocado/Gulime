package com.example.demo;

import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.repository.ProductRepository;
import com.example.demo.savings.api.ProfileRequest;
import com.example.demo.savings.api.SimulateRequest;
import com.example.demo.savings.catalog.JpaProductCatalog;
import com.example.demo.savings.domain.EmploymentType;
import com.example.demo.savings.service.ProductCatalog;
import com.example.demo.savings.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "simulator.catalog.type=db",
        "spring.datasource.url=jdbc:h2:mem:jpa-catalog-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class JpaProductCatalogIntegrationTests {

    private static final String DB_PRODUCT_ID = "DB_CATALOG_TEST_PRODUCT";

    @Autowired
    private ProductCatalog productCatalog;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SimulationService simulationService;

    @Test
    void simulationEngineReadsProductInsertedIntoDatabase() {
        productRepository.saveAndFlush(Product.builder()
                .productId(DB_PRODUCT_ID)
                .bankName(mojibake("테스트은행"))
                .productName(mojibake("DB 카탈로그 검증 상품"))
                .productType("DEPOSIT")
                .baseRate(new BigDecimal("3.25"))
                .maxRate(new BigDecimal("3.25"))
                .maxLimit(10_000_000L)
                .periodMonths(12)
                .isVerified(true)
                .build());

        assertThat(productCatalog).isInstanceOf(JpaProductCatalog.class);
        assertThat(productCatalog.findById(DB_PRODUCT_ID))
                .hasValueSatisfying(product -> {
                    assertThat(product.productName()).isEqualTo("DB 카탈로그 검증 상품");
                    assertThat(product.baseRate()).isEqualTo(0.0325);
                });

        var response = simulationService.simulate(new SimulateRequest(
                new ProfileRequest(
                        EmploymentType.FREELANCER,
                        false,
                        5_000_000L,
                        1_000_000L,
                        500_000L,
                        12,
                        List.of(0L, 0L, 0L, 0L, 0L, 0L),
                        0L,
                        List.of()
                ),
                List.of(DB_PRODUCT_ID)
        ));

        assertThat(response.products()).singleElement().satisfies(product -> {
            assertThat(product.productId()).isEqualTo(DB_PRODUCT_ID);
            assertThat(product.productName()).isEqualTo("DB 카탈로그 검증 상품");
            assertThat(product.baseRate()).isEqualTo(3.25);
            assertThat(product.expectedRate()).isEqualTo(3.25);
        });
    }

    private String mojibake(String value) {
        return new String(
                value.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1
        );
    }
}
