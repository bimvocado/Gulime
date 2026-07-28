# Malbokaz Windmill

금융상품 검증 데이터와 사용자 프로필을 바탕으로 예상 우대금리, 상품 조합,
12개월 실행 로드맵을 계산하는 Spring Boot 프로젝트입니다.

## 데이터 흐름

```text
data/validation_set.json
        ↓
JsonValidationProductCatalog
        ↓
ProductCatalog
        ↓
시뮬레이션 / 옵션 추천 / 로드맵 계산
```

`ProductCatalog`는 저장소 경계입니다. 현재 구현은 JSON 파일을 읽지만,
추후 JPA 구현으로 교체해도 계산 엔진과 API 코드는 변경하지 않아도 됩니다.

JSON의 `rate_by_term`에 만기별 금리가 있으면 하나의 원본 상품을
`PRODUCT_..._1M`, `PRODUCT_..._3M`, `PRODUCT_..._12M`과 같은 만기별
상품 variant로 변환합니다. 다중 `tiers`는 같은 tier 그룹에서 하나만,
상품 레벨 `selection_rule.max_select`가 있는 조건은 선택 그룹에서 지정된
개수만 적용합니다. `branch`와 `exclusive_group`도 중복 적용하지 않습니다.

## API

- `POST /api/v1/simulate`: 조건별 확률·신뢰구간·민감도와 예상금리 계산
- `POST /api/v1/options`: 목돈과 월 저축 여력을 자동 슬롯으로 만들고 Greedy 배분
- `POST /api/v1/roadmap`: 파킹·예금·적금 초기배분과 만기 재투입 로드맵 계산

기존 클라이언트 호환을 위해 `/simulate`, `/options`, `/roadmap` 경로도
동일한 계산 엔진으로 제공합니다.

`/options` 요청은 사용자가 상품 슬롯을 지정하지 않습니다. 온보딩의
`lumpSum`, `emergencyFund`, `monthlySaving`으로 엔진이 목돈 및 적금 슬롯을
생성하고 `STABLE`, `BALANCED`, `AGGRESSIVE` 세 가지 선택지를 반환합니다.

요청 예시는 `requests.http`를 참고하세요.

## 금리 단위

- `validation_set.json`: 퍼센트 숫자 (`3.5` = 3.5%)
- 계산 엔진 내부: 소수 비율 (`0.035`)
- API 응답: 퍼센트 숫자

JSON 카탈로그 어댑터가 입력 금리를 엔진 단위로 변환합니다.

## 실행

```bash
./gradlew bootRun
./gradlew test
```

Windows에서는 `gradlew.bat`을 사용합니다.

기본 상품 데이터 위치는 다음 설정으로 변경할 수 있습니다.

```properties
simulator.catalog.type=json
simulator.catalog.location=file:./data/validation_set.json
```

PostgreSQL 접속 정보는 배포 환경에서 `spring.datasource.*`로 설정합니다.
DB 카탈로그를 구현한 뒤 `simulator.catalog.type=db`로 전환할 수 있습니다.
