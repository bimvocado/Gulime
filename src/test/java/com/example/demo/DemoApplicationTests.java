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
		assertThat(productCatalog.findAll()).hasSize(49);
	}

	@Test
	void validationSetIsConvertedToEngineDomain() {
		SavingsProduct product = productCatalog.findAll().stream()
				.filter(item -> item.productName().equals("매일이자Wa파킹통장"))
				.findFirst()
				.orElseThrow();

		assertThat(product.baseRate()).isEqualTo(0.025);
		assertThat(product.maxRate()).isEqualTo(0.051);
		assertThat(product.conditions())
				.extracting(condition -> condition.type())
				.contains(
						ConditionType.BALANCE_MAINTENANCE,
						ConditionType.CHANNEL_USE
				);
	}

}
