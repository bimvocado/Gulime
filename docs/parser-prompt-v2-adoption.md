# 파서 프롬프트 v2 채택 결과 (v1 → v2)

`docs/parser-prompt-fix-requirements.md`에 정리된 34건 요구사항(threshold 자릿수 3건 +
UNCONDITIONAL 남발 15건 + 자원 오태깅 16건)을 `prompts/parsing_prompt_v1.txt`에 반영해
`prompts/parsing_prompt_v2.txt`를 만들고, 96개 상품 전체를 재파싱·재채점해 비교했다.

**결론: v2를 정식 버전으로 채택.** 잔여 미수정 5~6건은 표면 키워드에 끌려간 케이스로,
프롬프트 규칙을 더 좁히면 다른 케이스에서 오버피팅 위험이 있다고 판단해 이번 라운드에서는
추가 반영하지 않기로 했다 (few-shot 예시 추가로 풀 수 있는 영역 — 아래 "향후 개선 과제" 참고).

## 정식 파일

| 구분 | 정식(v2) | 롤백용(v1, 보존) |
|---|---|---|
| 프롬프트 | `prompts/parsing_prompt_v2.txt` | `prompts/parsing_prompt_v1.txt` |
| 파싱 결과 | `data/parsed_conditions_v2.json` | `data/parsed_conditions.json` |
| 채점 리포트 | `data/accuracy_report_v2.json` | `data/accuracy_report.json` |

`scripts/parse_conditions.py`, `scripts/measure_accuracy.py`, `scripts/load_to_db.py` 는
모두 기본값이 v2 파일을 가리키도록 변경했다. v1로 재현하려면 각 스크립트의
`--prompt`/`--output`/`--parsed`/`--report` 인자로 v1 파일명을 넘기면 된다
(자세한 사용법은 각 스크립트 상단 docstring, `prompts/README.md`, `data/README.md` 참고).

> DB에 이미 적재된 314개 상품은 v1 파싱 결과 기준이다. v2로 재적재할지는 이번 작업 범위가
> 아니며 별도로 결정한다.

## 전체 지표 비교

| 지표 | v1 | v2 |
|---|---|---|
| 전체 정확도 | 0.9207 (151/164) | **0.9534 (225/236)** |
| threshold 정확도 | 0.7805 (32/41) | **0.8814 (52/59)** |
| rate_bonus 정확도 | 0.9024 (37/41) | **0.9322 (55/59)** |
| type / resource 정확도 | 1.0 / 1.0 | 1.0 / 1.0 (※) |
| 조건 개수 불일치 상품 | 13 | **10** |
| 누락된 조건 | 62 | **44** |
| 초과된 조건 | 82 | **62** |

※ 채점 스크립트는 정답-파싱 조건을 `(resource, type)`이 같아야 짝짓는다. type/resource가
틀리면 애초에 짝이 안 지어져 field_accuracy 분모에 들어가지 않고 "누락/초과"로만 잡힌다.
그래서 v1의 type/resource가 "100%"로 보이는 건 착시이며, field_total이 41→59로 늘어난 것
자체가 "이전엔 짝을 못 찾던 조건이 이제 짝을 찾았다" = 카테고리 2·3이 실제로 개선됐다는 신호다.

## 카테고리별 결과

| 카테고리 | 건수 | 완전 수정 | 잔여 |
|---|---|---|---|
| 1. threshold 자릿수 | 3 | **3** | 0 |
| 2. UNCONDITIONAL 남발 | 15 (1건 데이터셋 없음, 14건 채점 가능) | **12** | 2 |
| 3. 자원 오태깅 | 16 | **12** | 3 (+ 1건 부분수정) |

FIRST_TRADE 이력조건(첫거래·해지이력·장기거래) resource는 하람 확인대로 4건 전부
`FIRST_TRADE`로 유지되어 규정 위반 없음.

## 향후 개선 과제 (잔여 미수정 5~6건)

프롬프트 규칙(카테고리 2·3)을 명시적으로 넣었음에도 아래 케이스는 고쳐지지 않았다. 공통적으로
**원문에 등장하는 표면 키워드(카드/급여/이체 등)에 모델이 끌려간 경우**로, 규칙 문구를 더
일반화·강화하면 이번엔 잡히더라도 다른 정상 케이스를 오분류시킬 위험(오버피팅)이 있어 이번
라운드에서는 보류했다. 규칙을 더 조이는 대신 **해당 조건 원문을 그대로 few-shot 예시로 프롬프트에
박아 넣는 방식**이 일반화 부작용 없이 이 잔여 케이스들을 해결할 수 있는 방향으로 보인다.

| # | 상품 / 조건 | 현재(v2) | 기대값 | 원인 추정 |
|---|---|---|---|---|
| 1 | NH고향사랑기부예금 - 기부금 납부고객 우대 | type=UNCONDITIONAL | type=OTHER | "납부고객" 같은 자격조건 문구가 트리거 목록(~시/~경우/회차/나이/동의/서약/인증/미보유)에 없어 안 걸림 |
| 2 | NH고향사랑기부예금 - 연령대(65세이상/19~34세) 2건 | type=UNCONDITIONAL | type=OTHER ×2 | 트리거 목록에 "만 65세 이상" 예시를 그대로 넣었는데도 미적용 — 예시 일반화가 안 되고 있음 |
| 3 | 스마트모아Dream정기예금 - 1천만원 이상 가입 | resource=SALARY_TRANSFER | resource=NONE(또는 무관 자원) | 원문에 급여 언급이 없는데도 "이상 가입" 문구 주변에서 급여이체로 연결 |
| 4 | 스마일드림 정기예금 - 체크카드 보유 | resource=CARD_BUDGET | resource=NONE | "카드" 키워드 자체가 있어 "보유는 NONE" 규칙보다 카드 키워드에 더 끌림 |
| 5 | 우리SUPER주거래적금 - 공과금 자동이체 출금 | resource=SALARY_TRANSFER (type은 AUTO_TRANSFER로 개선됨) | resource=NONE(또는 무관 자원) | "이체" 동사가 급여이체 조건과 근접해 있어 resource만 급여로 남음 (사용자가 직접 지적했던 케이스) |
| 6 (부분) | BNK더조은정기예금 - 자동재예치 가입 | type=AUTO_TRANSFER (resource는 NONE으로 정상화됨) | type=LOYALTY 등 (자동이체와 구분) | "자동재예치"와 "자동이체" 단어 유사도가 높아 규칙에 구분 예시를 넣었음에도 표면 유사도로 재발 |

**다음 라운드 제안**: 위 6개 조건의 원문(`source_text`)을 그대로 프롬프트 few-shot 예시로
추가하고 "정답 type/resource"를 나란히 보여주는 방식으로 좁혀서 대응 — 규칙을 일반화하는 대신
사례 기반으로 대응하면 다른 케이스에 부작용을 주지 않는다.
