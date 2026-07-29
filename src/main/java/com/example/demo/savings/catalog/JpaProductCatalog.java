package com.example.demo.savings.catalog;

import com.example.demo.domain.product.entity.Product;
import com.example.demo.domain.product.repository.ProductRepository;
import com.example.demo.savings.domain.*;
import com.example.demo.savings.service.ProductCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "simulator.catalog.type", havingValue = "db")
@Transactional(readOnly = true)
public class JpaProductCatalog implements ProductCatalog {
    private final ProductRepository repository;

    public JpaProductCatalog(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SavingsProduct> findById(String productId) {
        return repository.findById(productId).map(this::mapProduct);
    }

    @Override
    public List<SavingsProduct> findAll() {
        return repository.findAll().stream().map(this::mapProduct).toList();
    }

    private SavingsProduct mapProduct(Product product) {
        return new SavingsProduct(
                product.getProductId(),
                product.getProductName(),
                product.getBankName(),
                enumValue(ProductType.class, product.getProductType(), ProductType.DEPOSIT),
                product.getPeriodMonths(),
                0L,
                product.getMaxLimit() == null ? Long.MAX_VALUE : product.getMaxLimit(),
                ratio(product.getBaseRate()),
                ratio(product.getMaxRate()),
                product.getConditions().stream().map(condition -> mapCondition(product, condition)).toList()
        );
    }

    private com.example.demo.savings.domain.ProductCondition mapCondition(
            Product product,
            com.example.demo.domain.product.entity.ProductCondition condition
    ) {
        ConditionType type = conditionType(condition.getType());
        boolean selectable = Boolean.TRUE.equals(condition.getSelectable());
        Integer maxSelect = selectable ? product.getMaxSelect() : null;
        return new com.example.demo.savings.domain.ProductCondition(
                String.valueOf(condition.getConditionId()),
                fallback(condition.getConditionName(), condition.getSourceText()),
                condition.getSourceText(),
                type,
                condition.getThreshold(),
                resource(condition.getResource(), type),
                condition.getPeriodMonths(),
                condition.getRequiredMonths(),
                ratio(condition.getRateBonus()),
                Boolean.TRUE.equals(condition.getHardRequirement()),
                enumValue(Payout.class, condition.getPayoutType(), Payout.UNKNOWN),
                enumValue(ParseStatus.class, condition.getParseStatus(), ParseStatus.PARTIAL),
                condition.getTierGroup(),
                selectable ? fallback(condition.getSelectionGroup(), product.getProductId() + "_SELECTION") : null,
                selectable ? enumValue(SelectionRule.class, product.getSelectionRule(), SelectionRule.MAX_SELECT)
                        : SelectionRule.ALL,
                maxSelect,
                condition.getExclusiveGroup(),
                condition.getBranch()
        );
    }

    private ConditionType conditionType(String raw) {
        if (raw == null) return ConditionType.OTHER;
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "CARD_SPEND", "CARD_SPENDING", "CARD_USAGE" -> ConditionType.CARD_SPEND;
            case "CARD_ISSUE", "CARD_OWNERSHIP" -> ConditionType.CARD_OWNERSHIP;
            case "CARD_ACCOUNT", "CARD_PAYMENT_ACCOUNT" -> ConditionType.CARD_PAYMENT_ACCOUNT;
            case "SALARY", "SALARY_TRANSFER" -> ConditionType.SALARY_TRANSFER;
            case "FIRST_TRADE", "WELCOME_BONUS" -> ConditionType.FIRST_TRADE;
            case "AVG_BALANCE", "AVERAGE_BALANCE", "BALANCE_MAINTENANCE" ->
                    ConditionType.BALANCE_MAINTENANCE;
            case "MIN_DEPOSIT", "DEPOSIT_AMOUNT" -> ConditionType.DEPOSIT_AMOUNT;
            case "AUTO_TRANSFER", "TRANSFER_COUNT", "AUTOMATIC_TRANSFER" ->
                    ConditionType.TRANSFER_COUNT;
            case "PRODUCT_HOLDING", "CROSS_PRODUCT" -> ConditionType.PRODUCT_HOLDING;
            case "CHANNEL", "CHANNEL_USE", "ELECTRONIC_BANKING" -> ConditionType.CHANNEL_USE;
            case "MARKETING_AGREE", "MARKETING_AGREEMENT", "MARKETING_CONSENT",
                    "MARKETING_EVENT", "MYDATA" -> ConditionType.MARKETING_CONSENT;
            default -> ConditionType.OTHER;
        };
    }

    private ResourceType resource(String raw, ConditionType type) {
        ResourceType explicit = enumValue(ResourceType.class, raw, ResourceType.NONE);
        if (explicit != ResourceType.NONE) return explicit;
        return switch (type) {
            case CARD_SPEND -> ResourceType.CARD_BUDGET;
            case SALARY_TRANSFER -> ResourceType.SALARY_TRANSFER;
            case FIRST_TRADE -> ResourceType.FIRST_TRADE;
            case BALANCE_MAINTENANCE, DEPOSIT_AMOUNT -> ResourceType.CASH_BALANCE;
            default -> ResourceType.NONE;
        };
    }

    private double ratio(BigDecimal percent) {
        return percent == null ? 0.0 : percent.doubleValue() / 100.0;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
