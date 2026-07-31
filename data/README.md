# 파싱 결과 / 채점 리포트 버전 안내

| 파일 | 상태 | 비고 |
|---|---|---|
| `parsed_conditions_v2.json` | **정식 (기본값)** | `parsing_prompt_v2.txt`로 96개 상품 재파싱한 결과. `scripts/measure_accuracy.py`, `scripts/load_to_db.py` 기본 입력 |
| `accuracy_report_v2.json` | **정식 (기본값)** | 위 결과를 `validation_set.json`으로 채점한 리포트 |
| `parsed_conditions.json` | 롤백용 (보존) | `parsing_prompt_v1.txt` 결과. `--parsed parsed_conditions.json`으로 재현 가능 |
| `accuracy_report.json` | 롤백용 (보존) | v1 결과 채점 리포트 |

v2 채택 경위, v1 대비 개선/잔여 이슈는 [`docs/parser-prompt-v2-adoption.md`](../docs/parser-prompt-v2-adoption.md) 참고.

**주의**: 이미 DB에 적재된 314개 상품은 v1(`parsed_conditions.json`) 기준으로 적재되어 있다.
v2로 재적재하려면 `scripts/load_to_db.py`를 다시 실행해야 하는데, 기존 product_id는 중복 skip되므로
재적재 여부·범위는 별도로 논의 후 진행한다 (이번 작업 범위 아님).
