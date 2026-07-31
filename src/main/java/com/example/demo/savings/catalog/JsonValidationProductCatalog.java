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
import java.util.TreeMap;

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
                for (SavingsProduct product : toProducts(productNode)) {
                    if (loaded.putIfAbsent(product.productId(), product) != null) {
                        throw new IllegalStateException(
                                "중복 상품 ID가 생성되었습니다: "
                                        + product.productId()
                        );
                    }
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

    private List<SavingsProduct> toProducts(JsonNode node) {
        String productName = requiredText(node, "product_name");
        String bankName = text(node, "bank_name");
        if (bankName == null || bankName.isBlank()) {
            // 현재 검증셋의 일부 수기 작성 상품에는 은행명이 없습니다.
            // 데이터 자체는 계산에서 제외하지 않고, DB 적재 단계에서 보강할 수 있게 둡니다.
            bankName = "은행 미상";
        }
        String baseProductId = stableId(bankName, productName);
        List<TermRate> termRates = termRates(node);
        boolean usesTermVariants = hasTermRateDefinition(node);
        SelectionConfig selection = selectionConfig(node, baseProductId);

        List<SavingsProduct> products = new ArrayList<>();
        for (TermRate termRate : termRates) {
            String productId = usesTermVariants
                    ? baseProductId + "_" + termRate.termMonths() + "M"
                    : baseProductId;
            List<ProductCondition> conditions = new ArrayList<>();
            JsonNode conditionNodes = node.path("conditions");
            if (conditionNodes.isArray()) {
                int conditionIndex = 0;
                for (JsonNode conditionNode : conditionNodes) {
                    conditions.addAll(toConditions(
                            productId,
                            conditionIndex++,
                            conditionNode,
                            termRate.termMonths(),
                            selection
                    ));
                }
            }

            products.add(new SavingsProduct(
                    productId,
                    productName,
                    bankName,
                    productType(node, productName),
                    termRate.termMonths(),
                    nullableLong(node.get("minimum_amount"), 0L),
                    nullableLong(node.get("maximum_amount"), Long.MAX_VALUE),
                    percentToRatio(termRate.baseRate()),
                    percentToRatio(termRate.maxRate()),
                    conditions
            ));
        }
        return products;
    }

    private List<ProductCondition> toConditions(
            String productId,
            int conditionIndex,
            JsonNode node,
            int termMonths,
            SelectionConfig selection
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
                        tier,
                        termMonths,
                        tiers.size() > 1,
                        selection
                ));
            }
            return result;
        }

        // 마이그레이션 이전 형식(threshold/rate_bonus가 조건에 직접 존재)도 지원합니다.
        return List.of(toCondition(
                productId,
                conditionIndex,
                0,
                node,
                node,
                termMonths,
                false,
                selection
        ));
    }

    private ProductCondition toCondition(
            String productId,
            int conditionIndex,
            int tierIndex,
            JsonNode conditionNode,
            JsonNode valueNode,
            int termMonths,
            boolean tiered,
            SelectionConfig selection
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

        Integer conditionSelectLimit = selectableLimit(
                conditionNode.get("selectable")
        );
        boolean selectable = conditionSelectLimit != null;
        Integer maxSelect = selectable
                ? conditionSelectLimit > 1
                        ? conditionSelectLimit
                        : selection.maxSelect()
                : null;
        String selectionGroup = selectable
                ? selection.group()
                : null;
        String tierGroup = tiered
                ? productId + "_C" + conditionIndex + "_TIER"
                : null;

        return new ProductCondition(
                productId + "_C" + conditionIndex + "_T" + tierIndex,
                conditionName,
                sourceText,
                type,
                nullableLong(valueNode.get("threshold")),
                resource,
                nullableInteger(conditionNode.get("period_months")),
                nullableInteger(conditionNode.get("required_months")),
                percentToRatio(conditionRate(
                        conditionNode,
                        valueNode,
                        termMonths,
                        tiered
                )),
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
                tierGroup,
                selectionGroup,
                selectable ? SelectionRule.MAX_SELECT : SelectionRule.ALL,
                maxSelect,
                text(conditionNode, "exclusive_group"),
                text(conditionNode, "branch")
        );
    }

    private List<TermRate> termRates(JsonNode productNode) {
        double baseRate = nullableDouble(productNode.get("base_rate"), 0.0);
        double maxRate = nullableDouble(
                productNode.get("max_rate"),
                baseRate
        );
        double advertisedSpread = Math.max(0.0, maxRate - baseRate);
        Map<Integer, TermRate> rates = new TreeMap<>();

        JsonNode productRates = productNode.get("rate_by_term");
        if (isNonEmptyObject(productRates)) {
            for (Map.Entry<String, JsonNode> entry : productRates.properties()) {
                int term = termMonths(entry.getKey());
                JsonNode value = entry.getValue();
                double termBaseRate;
                double termMaxRate;
                if (value.isNumber()) {
                    termBaseRate = value.asDouble();
                    termMaxRate = termBaseRate + advertisedSpread;
                } else if (value.isObject()) {
                    termBaseRate = nullableDouble(
                            firstPresent(value, "base_rate", "rate"),
                            baseRate
                    );
                    termMaxRate = nullableDouble(
                            value.get("max_rate"),
                            termBaseRate + advertisedSpread
                    );
                } else {
                    throw new IllegalStateException(
                            "rate_by_term[" + entry.getKey()
                                    + "]은 숫자 또는 객체여야 합니다."
                    );
                }
                rates.put(term, new TermRate(term, termBaseRate, termMaxRate));
            }
        }

        JsonNode conditions = productNode.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode condition : conditions) {
                JsonNode conditionRates = condition.get("rate_by_term");
                if (!isNonEmptyObject(conditionRates)) {
                    continue;
                }
                for (Map.Entry<String, JsonNode> entry
                        : conditionRates.properties()) {
                    int term = termMonths(entry.getKey());
                    rates.putIfAbsent(
                            term,
                            new TermRate(term, baseRate, maxRate)
                    );
                }
            }
        }

        Integer explicitTerm = nullableInteger(productNode.get("period_months"));
        if (explicitTerm != null) {
            if (explicitTerm <= 0) {
                throw new IllegalStateException(
                        "period_months는 1 이상이어야 합니다."
                );
            }
            rates.putIfAbsent(
                    explicitTerm,
                    new TermRate(explicitTerm, baseRate, maxRate)
            );
        }
        if (rates.isEmpty()) {
            rates.put(
                    DEFAULT_TERM_MONTHS,
                    new TermRate(DEFAULT_TERM_MONTHS, baseRate, maxRate)
            );
        }
        return List.copyOf(rates.values());
    }

    private boolean hasTermRateDefinition(JsonNode productNode) {
        if (isNonEmptyObject(productNode.get("rate_by_term"))) {
            return true;
        }
        JsonNode conditions = productNode.path("conditions");
        if (conditions.isArray()) {
            for (JsonNode condition : conditions) {
                if (isNonEmptyObject(condition.get("rate_by_term"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Double conditionRate(
            JsonNode conditionNode,
            JsonNode tierNode,
            int termMonths,
            boolean tiered
    ) {
        JsonNode rates = conditionNode.get("rate_by_term");
        if (isNonEmptyObject(rates)) {
            JsonNode termRate = rates.get(Integer.toString(termMonths));
            if (termRate != null && !termRate.isNull()) {
                if (tiered) {
                    throw new IllegalStateException(
                            "다중 tiers와 rate_by_term을 한 조건에 동시에 "
                                    + "사용할 수 없습니다: "
                                    + text(conditionNode, "condition_name")
                    );
                }
                if (termRate.isNumber()) {
                    return termRate.asDouble();
                }
                if (termRate.isObject()) {
                    return nullableDouble(termRate.get("rate_bonus"), null);
                }
                throw new IllegalStateException(
                        "조건 rate_by_term[" + termMonths
                                + "]은 숫자 또는 객체여야 합니다."
                );
            }
        }
        return nullableDouble(tierNode.get("rate_bonus"), null);
    }

    private SelectionConfig selectionConfig(
            JsonNode productNode,
            String productId
    ) {
        JsonNode rule = productNode.get("selection_rule");
        int maxSelect = 1;
        if (rule != null && !rule.isNull()) {
            if (!rule.isObject()) {
                throw new IllegalStateException(
                        "selection_rule은 객체여야 합니다: " + productId
                );
            }
            Integer configured = nullableInteger(rule.get("max_select"));
            if (configured == null || configured <= 0) {
                throw new IllegalStateException(
                        "selection_rule.max_select는 1 이상이어야 합니다: "
                                + productId
                );
            }
            maxSelect = configured;
        }
        return new SelectionConfig(
                productId + "_CHOICE",
                maxSelect
        );
    }

    private ProductType productType(JsonNode node, String productName) {
        String explicit = text(node, "product_type");
        if (explicit == null || explicit.isBlank()) {
            return inferProductType(productName);
        }
        return switch (explicit.toUpperCase(Locale.ROOT)) {
            case "SAVING", "SAVINGS" -> ProductType.SAVING;
            case "PARKING" -> ProductType.PARKING;
            case "DEPOSIT" -> ProductType.DEPOSIT;
            default -> throw new IllegalStateException(
                    "지원하지 않는 product_type입니다: " + explicit
            );
        };
    }

    private int termMonths(String rawTerm) {
        try {
            int value = Integer.parseInt(rawTerm);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "rate_by_term의 키는 1 이상의 개월 수여야 합니다: "
                            + rawTerm,
                    exception
            );
        }
    }

    private boolean isNonEmptyObject(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty();
    }

    private JsonNode firstPresent(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private ConditionType normalizeConditionType(String rawType) {
        if (rawType == null) {
            return ConditionType.OTHER;
        }
        return switch (rawType.toUpperCase(Locale.ROOT)) {
            case "CARD_SPEND", "CARD_SPENDING", "CARD_USAGE" ->
                    ConditionType.CARD_SPEND;
            case "CARD_ISSUE", "CARD_OWNERSHIP" ->
                    ConditionType.CARD_OWNERSHIP;
            case "CARD_ACCOUNT", "CARD_PAYMENT_ACCOUNT" ->
                    ConditionType.CARD_PAYMENT_ACCOUNT;
            case "SALARY", "SALARY_TRANSFER" -> ConditionType.SALARY_TRANSFER;
            case "FIRST_TRADE", "WELCOME_BONUS" -> ConditionType.FIRST_TRADE;
            case "AVG_BALANCE", "AVERAGE_BALANCE", "BALANCE_MAINTENANCE" ->
                    ConditionType.BALANCE_MAINTENANCE;
            case "MIN_DEPOSIT", "DEPOSIT_AMOUNT" ->
                    ConditionType.DEPOSIT_AMOUNT;
            case "AUTO_TRANSFER", "TRANSFER_COUNT", "AUTOMATIC_TRANSFER" ->
                    ConditionType.TRANSFER_COUNT;
            case "PRODUCT_HOLDING", "CROSS_PRODUCT" ->
                    ConditionType.PRODUCT_HOLDING;
            case "CHANNEL", "CHANNEL_USE", "ELECTRONIC_BANKING" ->
                    ConditionType.CHANNEL_USE;
            case "MARKETING_AGREE", "MARKETING_AGREEMENT", "MARKETING_CONSENT",
                    "MARKETING_EVENT", "MYDATA" ->
                    ConditionType.MARKETING_CONSENT;
            case "UNCONDITIONAL" -> ConditionType.UNCONDITIONAL;
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

    private long nullableLong(JsonNode node, long defaultValue) {
        Long value = nullableLong(node);
        return value == null ? defaultValue : value;
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

    private record TermRate(
            int termMonths,
            double baseRate,
            double maxRate
    ) {
    }

    private record SelectionConfig(
            String group,
            int maxSelect
    ) {
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
