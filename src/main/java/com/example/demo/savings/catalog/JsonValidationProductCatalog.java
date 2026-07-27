package com.example.demo.savings.catalog;

import com.example.demo.savings.domain.ConditionType;
import com.example.demo.savings.domain.ParseStatus;
import com.example.demo.savings.domain.Payout;
import com.example.demo.savings.domain.ProductCondition;
import com.example.demo.savings.domain.ProductType;
import com.example.demo.savings.domain.ResourceType;
import com.example.demo.savings.domain.SavingsProduct;
import com.example.demo.savings.domain.SelectionRule;
import com.example.demo.savings.service.ProductCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * data/validation_set.json을 엔진의 도메인 모델로 변환합니다.
 *
 * <p>나중에 JPA 기반 구현을 추가할 때는 {@link ProductCatalog}의 구현체만
 * 교체하면 계산 엔진과 API는 그대로 유지할 수 있습니다.</p>
 */
@Component
@ConditionalOnProperty(
        name = "simulator.catalog.type",
        havingValue = "json",
        matchIfMissing = true
)
public class JsonValidationProductCatalog implements ProductCatalog {

    private static final int DEFAULT_TERM_MONTHS = 12;

    private final Map<String, SavingsProduct> products;

    public JsonValidationProductCatalog(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            @Value("${simulator.catalog.location:file:./data/validation_set.json}")
            String catalogLocation
    ) {
        Resource resource = resourceLoader.getResource(catalogLocation);
        this.products = Collections.unmodifiableMap(
                new LinkedHashMap<>(loadProducts(objectMapper, resource))
        );
    }

    @Override
    public Optional<SavingsProduct> findById(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public List<SavingsProduct> findAll() {
        return List.copyOf(products.values());
    }

    private Map<String, SavingsProduct> loadProducts(
            ObjectMapper objectMapper,
            Resource resource
    ) {
        try (InputStream input = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            if (!root.isArray()) {
                throw new IllegalStateException("상품 검증 데이터의 최상위 값은 배열이어야 합니다.");
            }

            Map<String, SavingsProduct> loaded = new LinkedHashMap<>();
            for (JsonNode productNode : root) {
                SavingsProduct product = toProduct(productNode);
                if (loaded.putIfAbsent(product.productId(), product) != null) {
                    throw new IllegalStateException(
                            "중복 상품 ID가 생성되었습니다: " + product.productId()
                    );
                }
            }
            if (loaded.isEmpty()) {
                throw new IllegalStateException("상품 검증 데이터가 비어 있습니다.");
            }
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "상품 검증 데이터를 읽을 수 없습니다: " + resource.getDescription(),
                    exception
            );
        }
    }

    private SavingsProduct toProduct(JsonNode node) {
        String productName = requiredText(node, "product_name");
        String bankName = text(node, "bank_name");
        if (bankName == null || bankName.isBlank()) {
            // 현재 검증셋의 일부 수기 작성 상품에는 은행명이 없습니다.
            // 데이터 자체는 계산에서 제외하지 않고, DB 적재 단계에서 보강할 수 있게 둡니다.
            bankName = "은행 미상";
        }
        String productId = stableId(bankName, productName);

        List<ProductCondition> conditions = new ArrayList<>();
        JsonNode conditionNodes = node.path("conditions");
        if (conditionNodes.isArray()) {
            int conditionIndex = 0;
            for (JsonNode conditionNode : conditionNodes) {
                conditions.addAll(toConditions(
                        productId,
                        conditionIndex++,
                        conditionNode
                ));
            }
        }

        return new SavingsProduct(
                productId,
                productName,
                bankName,
                inferProductType(productName),
                DEFAULT_TERM_MONTHS,
                0L,
                Long.MAX_VALUE,
                percentToRatio(nullableDouble(node.get("base_rate"), 0.0)),
                percentToRatio(nullableDouble(node.get("max_rate"), 0.0)),
                conditions
        );
    }

    private List<ProductCondition> toConditions(
            String productId,
            int conditionIndex,
            JsonNode node
    ) {
        JsonNode tiers = node.path("tiers");
        if (tiers.isArray() && !tiers.isEmpty()) {
            List<ProductCondition> result = new ArrayList<>();
            int tierIndex = 0;
            for (JsonNode tier : tiers) {
                result.add(toCondition(
                        productId,
                        conditionIndex,
                        tierIndex++,
                        node,
                        tier
                ));
            }
            return result;
        }

        // 마이그레이션 이전 형식(threshold/rate_bonus가 조건에 직접 존재)도 지원합니다.
        return List.of(toCondition(productId, conditionIndex, 0, node, node));
    }

    private ProductCondition toCondition(
            String productId,
            int conditionIndex,
            int tierIndex,
            JsonNode conditionNode,
            JsonNode valueNode
    ) {
        ConditionType type = normalizeConditionType(text(conditionNode, "type"));
        ResourceType resource = normalizeResource(
                text(conditionNode, "resource"),
                type
        );
        String conditionName = text(conditionNode, "condition_name");
        if (conditionName == null) {
            conditionName = text(conditionNode, "source_text");
        }
        if (conditionName == null) {
            conditionName = type.name();
        }
        String sourceText = text(conditionNode, "source_text");
        if (sourceText == null || sourceText.isBlank()) {
            sourceText = conditionName;
        }

        String selectionGroup = text(conditionNode, "exclusive_group");
        Integer maxSelect = selectableLimit(conditionNode.get("selectable"));

        return new ProductCondition(
                productId + "_C" + conditionIndex + "_T" + tierIndex,
                conditionName,
                sourceText,
                type,
                nullableLong(valueNode.get("threshold")),
                resource,
                nullableInteger(conditionNode.get("period_months")),
                nullableInteger(conditionNode.get("required_months")),
                percentToRatio(nullableDouble(valueNode.get("rate_bonus"), null)),
                conditionNode.path("hard_requirement").asBoolean(false),
                enumValue(
                        Payout.class,
                        text(conditionNode, "payout"),
                        Payout.UNKNOWN
                ),
                enumValue(
                        ParseStatus.class,
                        text(conditionNode, "parse_status"),
                        ParseStatus.PARTIAL
                ),
                selectionGroup,
                maxSelect == null ? SelectionRule.ALL : SelectionRule.MAX_SELECT,
                maxSelect,
                text(conditionNode, "branch")
        );
    }

    private ConditionType normalizeConditionType(String rawType) {
        if (rawType == null) {
            return ConditionType.OTHER;
        }
        return switch (rawType.toUpperCase(Locale.ROOT)) {
            case "CARD_SPEND", "CARD_SPENDING", "CARD_USAGE" ->
                    ConditionType.CARD_SPEND;
            case "CARD_OWNERSHIP" -> ConditionType.CARD_OWNERSHIP;
            case "CARD_PAYMENT_ACCOUNT" -> ConditionType.CARD_PAYMENT_ACCOUNT;
            case "SALARY", "SALARY_TRANSFER" -> ConditionType.SALARY_TRANSFER;
            case "FIRST_TRADE", "WELCOME_BONUS" -> ConditionType.FIRST_TRADE;
            case "AVG_BALANCE", "AVERAGE_BALANCE", "BALANCE_MAINTENANCE" ->
                    ConditionType.BALANCE_MAINTENANCE;
            case "DEPOSIT_AMOUNT" -> ConditionType.DEPOSIT_AMOUNT;
            case "TRANSFER_COUNT", "AUTOMATIC_TRANSFER" ->
                    ConditionType.TRANSFER_COUNT;
            case "PRODUCT_HOLDING", "CROSS_PRODUCT" ->
                    ConditionType.PRODUCT_HOLDING;
            case "CHANNEL", "CHANNEL_USE", "ELECTRONIC_BANKING" ->
                    ConditionType.CHANNEL_USE;
            case "MARKETING_AGREEMENT", "MARKETING_CONSENT",
                    "MARKETING_EVENT", "MYDATA" ->
                    ConditionType.MARKETING_CONSENT;
            default -> ConditionType.OTHER;
        };
    }

    private ResourceType normalizeResource(
            String rawResource,
            ConditionType type
    ) {
        ResourceType explicit = enumValue(
                ResourceType.class,
                rawResource,
                ResourceType.NONE
        );
        if (explicit != ResourceType.NONE) {
            return explicit;
        }
        return switch (type) {
            case CARD_SPEND -> ResourceType.CARD_BUDGET;
            case SALARY_TRANSFER -> ResourceType.SALARY_TRANSFER;
            case FIRST_TRADE -> ResourceType.FIRST_TRADE;
            case BALANCE_MAINTENANCE, DEPOSIT_AMOUNT ->
                    ResourceType.CASH_BALANCE;
            default -> ResourceType.NONE;
        };
    }

    private ProductType inferProductType(String productName) {
        if (productName.contains("적금")) {
            return ProductType.SAVING;
        }
        if (productName.contains("파킹") || productName.contains("통장")) {
            return ProductType.PARKING;
        }
        return ProductType.DEPOSIT;
    }

    private String stableId(String bankName, String productName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (bankName + "\u0000" + productName)
                            .getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder id = new StringBuilder("PRODUCT_");
            for (int index = 0; index < 8; index++) {
                id.append(String.format("%02X", hash[index]));
            }
            return id.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("필수 상품 필드가 없습니다: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private Long nullableLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private Integer nullableInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private Double nullableDouble(JsonNode node, Double defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return node.asDouble();
    }

    private Double percentToRatio(Double percent) {
        return percent == null ? null : percent / 100.0;
    }

    private Integer selectableLimit(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asInt();
        }
        return node.asBoolean(false) ? 1 : null;
    }

    private <E extends Enum<E>> E enumValue(
            Class<E> enumClass,
            String value,
            E defaultValue
    ) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }
}
