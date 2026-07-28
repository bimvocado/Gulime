"""
data/parsed_conditions.json (LLM 파싱 결과)와 data/validation_set.json (정답지, 42개)을
(product_name, bank_name)으로 매칭해 파싱 정확도를 측정한다.

채점 대상은 validation_set.json에 있는 42개 상품뿐이다. parsed_conditions.json에만
있는 나머지 상품은 채점에서 제외된다.

매칭 키는 (product_name, bank_name)이지만, bank_name은 collect_and_merge.py의
canonical_bank_name()(별칭 매핑 + 법인격 표기 제거)으로 정규화한 뒤 비교한다.
그래야 "SC제일은행" vs "한국스탠다드차타드은행", "케이뱅크" vs "주식회사 케이뱅크" 같은
표기 차이가 매칭 실패로 이어지지 않는다.

conditions 비교 방식:
- 두 파일 모두 조건의 tiers 배열은 항상 원소 1개([{"threshold": ..., "rate_bonus": ...}])이므로
  tiers[0]만 비교한다.
- 조건 개수가 다르면 그 자체를 별도로 기록하고, 필드 정확도는 두 리스트를 인덱스(등장 순서)로
  맞춰 min(len(parsed), len(validation))개만 짝지어 비교한다. 정답지에는 있지만 파싱 결과에
  짝이 없는 조건(누락)은 "조건 누락" 케이스로 별도 기록하며 필드 정확도 분모에는 넣지 않는다.

측정 필드: type, threshold, resource, rate_bonus (오차 0.01 이내면 정답)

사용법:
    python scripts/measure_accuracy.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

SCRIPTS_DIR = Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from collect_and_merge import canonical_bank_name  # noqa: E402

ROOT = SCRIPTS_DIR.parent
DATA_DIR = ROOT / "data"
PARSED_PATH = DATA_DIR / "parsed_conditions.json"
VALIDATION_PATH = DATA_DIR / "validation_set.json"
REPORT_PATH = DATA_DIR / "accuracy_report.json"

RATE_BONUS_TOLERANCE = 0.01
FIELDS = ["type", "threshold", "resource", "rate_bonus"]


def load_json(path: Path) -> Any:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def match_key(product_name: str | None, bank_name: str | None) -> tuple[str, str]:
    return (product_name or "", canonical_bank_name(bank_name))


def index_parsed(entries: list[dict]) -> dict[tuple[str, str], dict]:
    return {match_key(e.get("product_name"), e.get("bank_name")): e for e in entries}


def get_conditions(parsed_or_val_product: dict | None, key: str = "conditions") -> list[dict]:
    if parsed_or_val_product is None:
        return []
    if "parsed" in parsed_or_val_product:  # parsed_conditions.json 항목
        parsed = parsed_or_val_product.get("parsed")
        if not parsed:
            return []
        return parsed.get(key) or []
    return parsed_or_val_product.get(key) or []  # validation_set.json 항목


def first_tier(cond: dict) -> dict:
    tiers = cond.get("tiers") or []
    return tiers[0] if tiers else {"threshold": None, "rate_bonus": None}


def values_equal(a: Any, b: Any) -> bool:
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    return a == b


def rate_bonus_equal(a: float | None, b: float | None) -> bool:
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    return abs(a - b) <= RATE_BONUS_TOLERANCE


def compare_condition(parsed_cond: dict, val_cond: dict) -> dict[str, bool]:
    p_tier = first_tier(parsed_cond)
    v_tier = first_tier(val_cond)
    return {
        "type": values_equal(parsed_cond.get("type"), val_cond.get("type")),
        "resource": values_equal(parsed_cond.get("resource"), val_cond.get("resource")),
        "threshold": values_equal(p_tier.get("threshold"), v_tier.get("threshold")),
        "rate_bonus": rate_bonus_equal(p_tier.get("rate_bonus"), v_tier.get("rate_bonus")),
    }


def main() -> None:
    parsed_entries = load_json(PARSED_PATH)
    validation_entries = load_json(VALIDATION_PATH)
    parsed_by_key = index_parsed(parsed_entries)

    field_correct = {f: 0 for f in FIELDS}
    field_total = {f: 0 for f in FIELDS}

    unmatched_products: list[dict] = []
    condition_count_mismatches: list[dict] = []
    wrong_cases: list[dict] = []
    missing_conditions: list[dict] = []

    for val_product in validation_entries:
        product_name = val_product.get("product_name")
        bank_name = val_product.get("bank_name")
        key = match_key(product_name, bank_name)

        parsed_product = parsed_by_key.get(key)
        if parsed_product is None:
            unmatched_products.append({"product_name": product_name, "bank_name": bank_name})
            continue

        val_conditions = get_conditions(val_product)
        parsed_conditions = get_conditions(parsed_product)

        if len(val_conditions) != len(parsed_conditions):
            condition_count_mismatches.append(
                {
                    "product_name": product_name,
                    "bank_name": bank_name,
                    "validation_count": len(val_conditions),
                    "parsed_count": len(parsed_conditions),
                }
            )

        paired = min(len(val_conditions), len(parsed_conditions))
        for i in range(paired):
            val_cond = val_conditions[i]
            parsed_cond = parsed_conditions[i]
            result = compare_condition(parsed_cond, val_cond)
            v_tier = first_tier(val_cond)
            p_tier = first_tier(parsed_cond)

            for field, is_correct in result.items():
                field_total[field] += 1
                if is_correct:
                    field_correct[field] += 1
                else:
                    if field == "threshold":
                        expected, actual = v_tier.get("threshold"), p_tier.get("threshold")
                    elif field == "rate_bonus":
                        expected, actual = v_tier.get("rate_bonus"), p_tier.get("rate_bonus")
                    else:
                        expected, actual = val_cond.get(field), parsed_cond.get(field)

                    wrong_cases.append(
                        {
                            "product_name": product_name,
                            "bank_name": bank_name,
                            "condition_index": i,
                            "condition_name": val_cond.get("condition_name"),
                            "field": field,
                            "expected": expected,
                            "actual": actual,
                        }
                    )

        for i in range(paired, len(val_conditions)):
            missing_conditions.append(
                {
                    "product_name": product_name,
                    "bank_name": bank_name,
                    "condition_index": i,
                    "condition_name": val_conditions[i].get("condition_name"),
                }
            )

    field_accuracy = {
        f: (field_correct[f] / field_total[f] if field_total[f] else None) for f in FIELDS
    }
    total_correct = sum(field_correct.values())
    total_count = sum(field_total.values())
    overall_accuracy = total_correct / total_count if total_count else None

    report = {
        "summary": {
            "validation_products": len(validation_entries),
            "matched_products": len(validation_entries) - len(unmatched_products),
            "unmatched_products_count": len(unmatched_products),
            "condition_count_mismatches_count": len(condition_count_mismatches),
            "missing_conditions_count": len(missing_conditions),
            "overall_accuracy": overall_accuracy,
            "field_accuracy": field_accuracy,
            "field_totals": field_total,
        },
        "unmatched_products": unmatched_products,
        "condition_count_mismatches": condition_count_mismatches,
        "missing_conditions": missing_conditions,
        "wrong_cases": wrong_cases,
    }

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("=== 정확도 측정 결과 ===")
    print(f"검증셋 상품 수: {report['summary']['validation_products']}")
    print(f"매칭된 상품 수: {report['summary']['matched_products']}")
    if unmatched_products:
        print(f"매칭 실패(파싱 결과 없음): {len(unmatched_products)}개")
        for u in unmatched_products:
            print(f"  - {u['product_name']} ({u['bank_name']})")
    print(f"조건 개수 불일치: {len(condition_count_mismatches)}개 상품")
    print(f"누락된 조건(정답지엔 있으나 파싱 결과에 짝이 없음): {len(missing_conditions)}개")
    print()
    print(f"전체 정확도: {overall_accuracy:.4f} ({total_correct}/{total_count})" if total_count else "전체 정확도: N/A")
    print("항목별 정확도:")
    for f in FIELDS:
        acc = field_accuracy[f]
        total = field_total[f]
        correct = field_correct[f]
        print(f"  - {f}: {acc:.4f} ({correct}/{total})" if total else f"  - {f}: N/A")

    print()
    print(f"틀린 케이스: {len(wrong_cases)}개")
    for w in wrong_cases:
        print(
            f"  - [{w['product_name']} / {w['bank_name']}] "
            f"condition[{w['condition_index']}]({w['condition_name']}) "
            f"{w['field']}: 정답={w['expected']!r} vs 파싱={w['actual']!r}"
        )

    print(f"\n리포트 저장: {REPORT_PATH}")


if __name__ == "__main__":
    main()
