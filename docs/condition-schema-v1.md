# Condition 스키마 v1 + 파싱 프롬프트

> 실물 원문 9건 분석 반영 (KB 5 + 카드/급여 4)
> 확정 후 C의 검증셋 · B의 파싱 · A의 확률엔진이 모두 이 계약을 따름

---

# 1. 단위 규칙 (⚠️ 먼저 확정)

| 항목 | 단위 | 예 |
|---|---|---|
| 금리 (`rate_bonus`, `base_rate`) | **퍼센트 숫자** | `0.5` = 0.5%p |
| 금액 (`threshold`) | **원** | `300000` = 30만원 |
| 기간 (`period_months`) | **개월** | `12` |

> ❗ C가 기존에 쓴 `0.005` 형식은 `0.5`로 변환 필요.
> A의 확률 엔진도 이 규칙 사용. **여기서 어긋나면 계산 전부 틀어짐.**

---

# 2. 상품 스키마

```json
{
  "product_name": "KB내맘대로적금",
  "bank_name": "국민은행",
  "product_type": "SAVING",
  "base_rate": 2.15,
  "max_rate": 2.75,
  "selection_rule": {
    "max_select": 6,
    "total_options": 9
  },
  "conditions": [ ... ]
}
```

| 필드 | 설명 |
|---|---|
| `product_type` | `DEPOSIT`(예금) / `SAVING`(적금) / `PARKING`(파킹·수기등록) |
| `selection_rule` | **선택형 상품에만.** 없으면 null (모든 조건 독립 평가) |

---

# 3. Condition 스키마

```json
{
  "condition_name": "급여이체",
  "type": "SALARY_TRANSFER",
  "resource": "SALARY_TRANSFER",
  "tiers": [
    { "threshold": null, "rate_bonus": 0.1 }
  ],
  "rate_by_term": null,
  "period_months": null,
  "required_months": null,
  "hard_requirement": false,
  "payout": "ALL_OR_NOTHING",
  "branch": null,
  "exclusive_group": null,
  "selectable": true,
  "parse_status": "COMPLETE",
  "source_text": "우대이율 항목 : 급여이체, 카드결제계좌, ..."
}
```

## 필드 정의

| 필드 | 설명 |
|---|---|
| `condition_name` | 원문의 조건명. XAI 근거 텍스트에 사용 |
| `type` | 조건 유형 (아래 enum) |
| `resource` | **소모하는 한정 자원** (아래 enum). 최적화 제약의 핵심 |
| `tiers` | 계단식 조건. 단일이면 원소 1개 |
| `rate_by_term` | 계약기간별 차등 시. `{"12": 0.6, "24": 0.7, "36": 0.9}` |
| `period_months` | 조건 평가 기간 |
| `required_months` | 그중 몇 개월 달성해야 하는지 |
| `hard_requirement` | true면 미달 시 **가입 자체 불가** |
| `payout` | 아래 enum |
| `branch` | 배타 분기. `NEW_CUSTOMER` / `EXISTING_CUSTOMER` / null |
| `exclusive_group` | 같은 값끼리 중복 적용 불가 (그룹명 문자열) |
| `selectable` | `selection_rule` 있는 상품에서 선택 대상인지 |
| `parse_status` | `COMPLETE` / `PARTIAL` / `FAILED` |
| `source_text` | 근거 원문 (검수·XAI용) |

## enum: type

| 값 | 설명 | 실물 예시 |
|---|---|---|
| `SALARY_TRANSFER` | 급여·연금 이체 | "급여 또는 연금입금시" |
| `CARD_SPEND` | 카드 결제실적 (금액) | "전월결제금 300만원이상" |
| `CARD_ISSUE` | 카드 신규 발급 | "신용(체크)카드를 최초로 발급" |
| `CARD_ACCOUNT` | 카드 결제계좌 지정 | "카드결제계좌" |
| `FIRST_TRADE` | 첫거래·신규고객 | "첫 거래", "신규고객" |
| `MIN_DEPOSIT` | 최소 예치·납입 금액 | "월부금이 50만원 이상" |
| `AVG_BALANCE` | 요구불 평잔 유지 | "요구불평잔 300만원이상" |
| `AUTO_TRANSFER` | 자동이체 (공과금·저축) | "공과금 자동이체시" |
| `MARKETING_AGREE` | 마케팅·정보수집 동의 | "마케팅동의시" |
| `CHANNEL` | 가입 채널 제한 | "인터넷/모바일뱅킹 가입" |
| `PRODUCT_HOLDING` | 타 상품 보유·교차거래 | "주택청약종합저축" |
| `LOYALTY` | 거래기간·장기거래 | "거래기간 5년이상" |
| `UNCONDITIONAL` | ⭐ **조건 아님. 전원 적용** | "특판우대이율 0.50%p" |
| `OTHER` | 위 어디에도 없음 | "별 모으기", "퀴즈미션" |

> 🔥 **`UNCONDITIONAL`이 가장 중요합니다.**
> 이걸 조건으로 잘못 파싱하면 달성확률을 곱해서 **금리를 부당하게 깎습니다.**
> "특판우대", "○개월 우대이율"처럼 **사용자 행동과 무관한 것**은 전부 여기.

## enum: resource

| 값 | 성격 | 제약 |
|---|---|---|
| `CARD_BUDGET` | 분할 가능 예산 | Σ 요구액 ≤ 월 카드 사용 가능액 |
| `SALARY_TRANSFER` | 배타적 | 전체 조합에서 최대 1개 |
| `FIRST_TRADE` | 은행별 1회 소모 | 은행당 1개 |
| `CASH_BALANCE` | ⭐ 유동자금 | 요구불 평잔. **비상금 슬롯과 경쟁** |
| `NONE` | 자원 소모 없음 | 제약 없음 |

## enum: payout

| 값 | 의미 |
|---|---|
| `ALL_OR_NOTHING` | 전 기간 충족해야 전액 지급 |
| `PRORATED` | 달성 개월수 비례 지급 |
| `AT_MATURITY` | 만기 시점 1회 평가 |
| `UNKNOWN` | 원문에서 판단 불가 |

---

# 4. 실물 파싱 예시

## 예시 A — 선택형 (KB내맘대로적금)

원문:
```
신규 시 9가지 우대이율 항목 중 6가지를 자유롭게 선택하고,
적용조건 충족 시 항목 당 각 연0.1%p (최고 연0.6%p)
- 급여이체, 카드결제계좌, 자동이체 저축, 아파트관리비 이체,
  KB스타뱅킹 이체, 장기거래, 첫 거래, 주택청약종합저축, 소중한 날
```

```json
{
  "selection_rule": { "max_select": 6, "total_options": 9 },
  "conditions": [
    { "condition_name": "급여이체", "type": "SALARY_TRANSFER",
      "resource": "SALARY_TRANSFER", "tiers": [{"threshold": null, "rate_bonus": 0.1}],
      "selectable": true, "parse_status": "COMPLETE" },
    { "condition_name": "카드결제계좌", "type": "CARD_ACCOUNT",
      "resource": "NONE", "tiers": [{"threshold": null, "rate_bonus": 0.1}],
      "selectable": true, "parse_status": "COMPLETE" },
    { "condition_name": "첫 거래", "type": "FIRST_TRADE",
      "resource": "FIRST_TRADE", "tiers": [{"threshold": null, "rate_bonus": 0.1}],
      "selectable": true, "parse_status": "COMPLETE" }
  ]
}
```
> 나머지 6개(자동이체·아파트관리비·KB스타뱅킹·장기거래·주택청약·소중한날)도 동일 형식

## 예시 B — 계단식 + 유동자금 경쟁 (미즈월복리정기예금)

원문:
```
① 요구불평잔 : 0.2% -300만원이상 0.1%, 500만원이상 0.2%
② 신용(체크)카드결제실적 : 0.1% -전월결제금 300만원이상 0.05%, 500만원이상 0.1%
```

```json
[
  { "condition_name": "요구불평잔", "type": "AVG_BALANCE",
    "resource": "CASH_BALANCE",
    "tiers": [
      {"threshold": 3000000, "rate_bonus": 0.1},
      {"threshold": 5000000, "rate_bonus": 0.2}
    ],
    "payout": "PRORATED", "parse_status": "COMPLETE" },

  { "condition_name": "신용(체크)카드결제실적", "type": "CARD_SPEND",
    "resource": "CARD_BUDGET",
    "tiers": [
      {"threshold": 3000000, "rate_bonus": 0.05},
      {"threshold": 5000000, "rate_bonus": 0.1}
    ],
    "period_months": 1, "payout": "PRORATED", "parse_status": "COMPLETE" }
]
```

## 예시 C — 배타 분기 (오면우대! 하면우대! 정기적금)

원문:
```
신규고객 ①적금가입시3.0% ②마케팅동의시0.1%
        ③신규월 포함 3개월 동안 10만원 이상 카드 대금결제시2.0%
기존고객 ①급여 또는 연금입금시1.5% ②공과금 자동이체시 2.0%
        ③카드이용시(8회이상&10만원이상)1.5% ④마케팅동의시0.1%
```

```json
[
  { "condition_name": "신규고객 적금가입", "type": "FIRST_TRADE",
    "resource": "FIRST_TRADE", "branch": "NEW_CUSTOMER",
    "tiers": [{"threshold": null, "rate_bonus": 3.0}],
    "parse_status": "COMPLETE" },

  { "condition_name": "신규고객 카드대금결제", "type": "CARD_SPEND",
    "resource": "CARD_BUDGET", "branch": "NEW_CUSTOMER",
    "tiers": [{"threshold": 100000, "rate_bonus": 2.0}],
    "period_months": 3, "required_months": 3,
    "payout": "ALL_OR_NOTHING", "parse_status": "COMPLETE" },

  { "condition_name": "기존고객 급여/연금입금", "type": "SALARY_TRANSFER",
    "resource": "SALARY_TRANSFER", "branch": "EXISTING_CUSTOMER",
    "tiers": [{"threshold": null, "rate_bonus": 1.5}],
    "parse_status": "COMPLETE" }
]
```
> ⚠️ `branch`가 다른 조건은 **동시 적용 불가.** 확률 엔진이 분기별로 따로 계산해야 함.

## 예시 D — 파싱 불가 (KB 특★한 적금)

원문:
```
② 별 모으기 우대이율 : 최고 연 1.0%p  10개: 연 0.5%p, 20개: 연 1.0%p
③ 함께해요 우대이율: 최고 연 2.0%p
```

```json
[
  { "condition_name": "별 모으기", "type": "OTHER", "resource": "NONE",
    "tiers": [{"threshold": 10, "rate_bonus": 0.5}, {"threshold": 20, "rate_bonus": 1.0}],
    "parse_status": "PARTIAL",
    "source_text": "별 모으기 우대이율 : 10개 연 0.5%p, 20개 연 1.0%p" },

  { "condition_name": "함께해요", "type": "OTHER", "resource": "NONE",
    "tiers": [{"threshold": null, "rate_bonus": 2.0}],
    "parse_status": "FAILED",
    "source_text": "함께해요 우대이율: 최고 연 2.0%p" }
]
```

> 🔑 **`PARTIAL`/`FAILED` 조건은 기대금리 계산에서 제외.**
> 발표 멘트: *"파싱 불가 조건은 기대금리에 반영하지 않았습니다.
> 저희는 모르는 것을 안다고 하지 않습니다."*
> → 억지 숫자보다 훨씬 강한 방어

---

# 5. 파싱 프롬프트 v1

```
당신은 한국 은행 예적금 상품의 우대조건 텍스트를 구조화하는 파서입니다.
입력된 우대조건 원문을 아래 JSON 스키마로 변환하세요.

## 출력 규칙
- JSON만 출력. 마크다운 코드블록(```)이나 설명 문장 금지.
- 원문에 없는 정보는 절대 추측하지 말 것. 모르면 null.
- 금리는 퍼센트 숫자로 (0.5%p → 0.5). 금액은 원 단위로 (30만원 → 300000).

## 출력 형식
{
  "selection_rule": { "max_select": 정수, "total_options": 정수 } 또는 null,
  "conditions": [
    {
      "condition_name": "원문에 나온 조건 이름",
      "type": "아래 type enum 중 하나",
      "resource": "아래 resource enum 중 하나",
      "tiers": [ { "threshold": 숫자 또는 null, "rate_bonus": 숫자 또는 null } ],
      "rate_by_term": { "12": 숫자, "24": 숫자 } 또는 null,
      "period_months": 정수 또는 null,
      "required_months": 정수 또는 null,
      "hard_requirement": true/false,
      "payout": "ALL_OR_NOTHING | PRORATED | AT_MATURITY | UNKNOWN",
      "branch": "NEW_CUSTOMER | EXISTING_CUSTOMER | null",
      "exclusive_group": "문자열 또는 null",
      "selectable": true/false,
      "parse_status": "COMPLETE | PARTIAL | FAILED",
      "source_text": "해당 조건의 원문 일부"
    }
  ]
}

## type enum
SALARY_TRANSFER  급여·연금 이체
CARD_SPEND       카드 결제실적 (금액 기준)
CARD_ISSUE       카드 신규 발급
CARD_ACCOUNT     카드 결제계좌 지정
FIRST_TRADE      첫거래·신규고객·미보유
MIN_DEPOSIT      최소 예치금액·월납입액
AVG_BALANCE      요구불 평잔 유지
AUTO_TRANSFER    자동이체 (공과금·저축 등)
MARKETING_AGREE  마케팅·개인정보 동의
CHANNEL          가입 채널 제한 (인터넷·모바일 전용 등)
PRODUCT_HOLDING  타 상품 보유·교차거래·청약
LOYALTY          거래기간·장기거래
UNCONDITIONAL    사용자 행동과 무관하게 전원 적용
OTHER            위 어디에도 해당 없음

## resource enum
CARD_BUDGET      카드 사용액을 소모 (CARD_SPEND 계열)
SALARY_TRANSFER  급여이체 계좌를 점유 (한 곳만 가능)
FIRST_TRADE      첫거래 자격을 소모 (은행당 1회)
CASH_BALANCE     유동자금을 묶음 (AVG_BALANCE 계열)
NONE             한정 자원을 소모하지 않음

## 중요 판별 규칙

1. UNCONDITIONAL 판별 (가장 중요)
   "특판우대이율", "○개월 우대이율", "계약기간별 우대"처럼
   사용자가 아무것도 하지 않아도 적용되는 것은 type=UNCONDITIONAL, resource=NONE.
   → 이걸 조건으로 잘못 분류하면 금리가 부당하게 깎입니다.

2. 계단식 조건
   "300만원이상 0.05%, 500만원이상 0.1%" 처럼 금액대별로 다르면
   tiers 배열에 여러 원소로 표현.

3. 선택형 상품
   "9가지 중 6가지 선택" 같은 문구가 있으면
   selection_rule 을 채우고 각 조건에 selectable=true.

4. 배타 분기
   "신규고객 / 기존고객" 처럼 그룹이 나뉘면 branch 에 표시.
   둘은 동시에 적용될 수 없습니다.

5. parse_status 판정
   COMPLETE : 조건과 금리가 모두 명확
   PARTIAL  : 금리는 있으나 달성 조건이 모호 (예: "별 20개 모으기")
   FAILED   : 무엇을 해야 하는지 원문만으로 알 수 없음
   → 모호하면 억지로 COMPLETE 하지 말 것. PARTIAL/FAILED 가 정답입니다.

## 입력
상품명: {product_name}
은행명: {bank_name}
우대조건 원문:
{spcl_cnd}
```

---

# 6. 팀 전달 사항

## → C
- `condition_name`, `required_months`, `parse_status` 채택 (C 제안 반영)
- ⚠️ **단위 변경**: `0.005` → `0.5` (퍼센트 숫자)
- 신규 필드: `tiers`, `branch`, `exclusive_group`, `selectable`, `source_text`, `rate_by_term`
- `selection_rule`은 상품 레벨
- 기존 12개는 마이그레이션 스크립트로 자동 변환 (C 손댈 필요 없음)

## → A (확률 엔진)
- `resource` 별 제약:
  - `CARD_BUDGET` → Σ threshold ≤ 월 카드 예산
  - `SALARY_TRANSFER` → 전체 조합에서 1개
  - `FIRST_TRADE` → 은행당 1개
  - **`CASH_BALANCE` → 비상금 슬롯과 경쟁** ⭐ 신규
- `branch` 다르면 동시 적용 불가 → 분기별 별도 계산
- `selection_rule` 있으면 max_select 개까지만 선택
- **`parse_status != COMPLETE` 는 기대금리 계산에서 제외**
- 단위: 금리=퍼센트숫자, 금액=원

## 데모 시나리오 확정
**KB내맘대로적금** — 9개 중 6개 선택, 각 0.1%p
→ 프리랜서: 급여이체 불가 / 아파트관리비 불가(자취) / 주택청약 미보유
→ **6개를 채울 수 없어 최고금리 구조적 도달 불가**
→ KB 상품으로 우리 주장을 그대로 증명