# Malbokaz Windmill

은행 예적금의 우대조건 달성 확률을 추정하여, 광고용 최고금리가 아닌 '사용자가 실제로 받을 기대금리'를 진단하고, 한정된 자원(카드실적·급여이체·첫거래 등) 제약 하에서 12개월 자금배분을 설계하는 AI 서비스입니다.

## 실행 방법

### 1. 환경변수 설정

`.env.example`을 복사하여 `.env` 파일을 만들고, 각 API 키와 DB 접속 정보를 입력합니다.

```bash
copy .env.example .env    # macOS/Linux: cp .env.example .env
```

| 변수 | 설명 |
|------|------|
| `FSS_API_KEY` | 금융감독원 금융상품 오픈API 키 |
| `DB_URL` | PostgreSQL(Neon) 접속 URL |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `UPSTAGE_API_KEY` | 업스테이지 Solar API 키 (우대조건 파싱용) |

### 2. 백엔드 실행 (Spring Boot)

```bash
gradlew bootRun         # Windows
./gradlew bootRun       # macOS/Linux
```

`Started DemoApplication` 이 출력되면 `localhost:8080`에서 API가 준비됩니다.

### 3. 프론트엔드 실행 (React + Vite)

```bash
npm install
npm run dev
```

출력된 로컬 주소(예: `http://localhost:5173`)를 브라우저에서 엽니다.
백엔드를 먼저 실행한 뒤 프론트엔드를 실행해야 합니다.

### 4. (선택) 데이터 파싱·적재 재현

```bash
python scripts/parse_conditions.py    # Solar API로 우대조건 파싱
python scripts/measure_accuracy.py    # 파싱 정확도 측정
python scripts/load_to_db.py          # DB 적재
```

## 주요 기능

- **우대조건 파싱**: 금감원 금융상품 데이터를 업스테이지 Solar API(LLM)로 파싱하여 우대조건을 구조화합니다. 원문 기반 검증으로 파싱 정확도 95.3%를 달성했습니다.
- **기대금리 진단**: 사용자의 자원 상황을 바탕으로 각 우대조건의 달성 확률을 추정하고, 실제로 받을 수 있는 기대금리(E[r])를 계산합니다.
- **자원 제약 최적화**: 카드실적·급여이체·첫거래 등 한정된 자원이 여러 우대조건 사이에서 경쟁하는 구조를 모델링하여, 자원 제약 하 상품 조합과 자금배분을 계산합니다.

## 기술 스택

- **백엔드**: Spring Boot (JDK 17), Gradle
- **프론트엔드**: React + Vite
- **데이터베이스**: PostgreSQL (Neon)
- **파싱 LLM**: 업스테이지 Solar API
- **데이터 소스**: 금융감독원 금융상품 오픈API

## 데이터 흐름

```text
금감원 오픈API → raw_products.json
        ↓ (Solar API 파싱)
parsed_conditions_v2.json
        ↓ (적재)
PostgreSQL (Neon)
        ↓ (JpaProductCatalog)
계산 엔진 (시뮬레이션 / 옵션 추천 / 로드맵)
```

`ProductCatalog`는 저장소 경계 인터페이스입니다. 계산 엔진과 API 코드를 변경하지 않고 데이터 소스를 교체할 수 있으며, 현재 구현은 PostgreSQL(`JpaProductCatalog`)을 사용합니다.

만기별 금리가 있는 원본 상품은 `..._6M`, `..._12M`, `..._24M`, `..._36M`과 같이 만기별 상품으로 분리하여 적재합니다. 다중 `tiers`는 같은 tier 그룹에서 하나만, 상품 레벨 `selection_rule.max_select`가 있는 조건은 선택 그룹에서 지정된 개수만 적용하며, `branch`와 `exclusive_group`도 중복 적용하지 않습니다.

## API

- `POST /api/v1/simulate`: 조건별 확률·신뢰구간·민감도와 예상금리 계산
- `POST /api/v1/options`: 목돈과 월 저축 여력을 자동 슬롯으로 만들고 상품 배분 (STABLE / BALANCED / AGGRESSIVE 세 가지 반환)
- `POST /api/v1/roadmap`: 파킹·예금·적금 초기배분과 만기 재투입 로드맵 계산

요청 예시는 `requests.http`를 참고하세요.

`/options` 요청은 사용자가 상품 슬롯을 지정하지 않습니다. 온보딩에서 입력한 `lumpSum`, `emergencyFund`, `monthlySaving`으로 엔진이 목돈 및 적금 슬롯을 생성합니다.

## 금리 단위

- 원본 데이터: 퍼센트 숫자 (`3.5` = 3.5%)
- 계산 엔진 내부: 소수 비율 (`0.035`)
- API 응답: 퍼센트 숫자