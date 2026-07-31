# 파싱 프롬프트 버전 안내

| 파일 | 상태 | 비고 |
|---|---|---|
| `parsing_prompt_v2.txt` | **정식 (기본값)** | `scripts/parse_conditions.py` 기본 프롬프트 |
| `parsing_prompt_v1.txt` | 롤백용 (보존) | `--prompt parsing_prompt_v1.txt` 로 재현 가능 |

v2 채택 경위, v1 대비 개선/잔여 이슈는 [`docs/parser-prompt-v2-adoption.md`](../docs/parser-prompt-v2-adoption.md) 참고.

v1 파일은 삭제하지 않고 그대로 둔다 — 롤백 및 회귀 비교 기준선으로 계속 사용.
