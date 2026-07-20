"""
data/validation_set.json 을 새 스키마로 변환한다.

변경 내용:
- base_rate/max_rate(상품 단위), rate_bonus(조건 단위): 소수 비율 -> %p 값 (x100)
- 조건의 threshold/rate_bonus를 tiers 배열로 감싸기
  {"threshold": t, "rate_bonus": r} -> {"tiers": [{"threshold": t, "rate_bonus": r}]}
- 신규 필드 추가, 기본값 null
  - 상품 단위: rate_by_term
  - 조건 단위: branch, exclusive_group, selectable, source_text
- type enum 매핑
  BALANCE_MAINTENANCE -> AVG_BALANCE
  CHANNEL_USE -> CHANNEL
  PRODUCT_HOLDING, CARD_OWNERSHIP -> PRODUCT_HOLDING
  (그 외 type은 변경하지 않음)

실행 전 원본을 data/validation_set.json.bak 으로 백업하고,
변환 후 json.load()로 파싱 검증한다.

사용법:
    python scripts/migrate_validation_schema.py
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VALIDATION_SET_PATH = ROOT / "data" / "validation_set.json"
BACKUP_PATH = VALIDATION_SET_PATH.with_suffix(".json.bak")

TYPE_MAPPING = {
    "BALANCE_MAINTENANCE": "AVG_BALANCE",
    "CHANNEL_USE": "CHANNEL",
    "PRODUCT_HOLDING": "PRODUCT_HOLDING",
    "CARD_OWNERSHIP": "PRODUCT_HOLDING",
}

CONDITION_META_DEFAULTS = {
    "branch": None,
    "exclusive_group": None,
    "selectable": None,
    "source_text": None,
}


def to_percent(value):
    """0.005 -> 0.5 처럼 비율을 %p 값으로 바꾼다. None은 그대로 둔다."""
    if value is None:
        return None
    return round(value * 100, 6)


def transform_condition(cond: dict) -> dict:
    new_type = TYPE_MAPPING.get(cond.get("type"), cond.get("type"))
    tiers = [
        {
            "threshold": cond.get("threshold"),
            "rate_bonus": to_percent(cond.get("rate_bonus")),
        }
    ]

    new_cond: dict = {}
    for key in cond.keys():
        if key in ("threshold", "rate_bonus"):
            continue
        if key == "type":
            new_cond["type"] = new_type
            new_cond["tiers"] = tiers
        else:
            new_cond[key] = cond[key]

    if "parse_status" in new_cond:
        final: dict = {}
        for key, value in new_cond.items():
            if key == "parse_status":
                final.update(CONDITION_META_DEFAULTS)
            final[key] = value
        new_cond = final
    else:
        new_cond.update(CONDITION_META_DEFAULTS)

    return new_cond


def transform_product(product: dict) -> dict:
    new_product: dict = {}
    for key in product.keys():
        if key == "base_rate":
            new_product["base_rate"] = to_percent(product["base_rate"])
        elif key == "max_rate":
            new_product["max_rate"] = to_percent(product["max_rate"])
            new_product["rate_by_term"] = None
        elif key == "conditions":
            new_product["conditions"] = [transform_condition(c) for c in product["conditions"]]
        else:
            new_product[key] = product[key]
    return new_product


def validate(migrated: list[dict], original_count: int) -> None:
    assert isinstance(migrated, list), "최상위가 배열이 아닙니다"
    assert len(migrated) == original_count, (
        f"항목 개수가 바뀌었습니다: {original_count} -> {len(migrated)}"
    )
    for product in migrated:
        assert "rate_by_term" in product, f"rate_by_term 누락: {product.get('product_name')}"
        for cond in product.get("conditions", []):
            assert "tiers" in cond, f"tiers 누락: {product.get('product_name')}"
            assert "threshold" not in cond, f"threshold가 남아있음: {product.get('product_name')}"
            assert "rate_bonus" not in cond, f"rate_bonus가 남아있음: {product.get('product_name')}"
            for field in CONDITION_META_DEFAULTS:
                assert field in cond, f"{field} 누락: {product.get('product_name')}"


def main() -> None:
    with open(VALIDATION_SET_PATH, "r", encoding="utf-8") as f:
        original = json.load(f)

    shutil.copy2(VALIDATION_SET_PATH, BACKUP_PATH)
    print(f"백업 완료: {BACKUP_PATH}")

    migrated = [transform_product(p) for p in original]

    with open(VALIDATION_SET_PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(migrated, f, ensure_ascii=False, indent=2)
        f.write("\n")

    with open(VALIDATION_SET_PATH, "r", encoding="utf-8") as f:
        reparsed = json.load(f)

    validate(reparsed, len(original))

    print(f"변환 완료: {VALIDATION_SET_PATH}")
    print(f"json.load() 파싱 성공: True")
    print(f"항목 개수: {len(reparsed)} (원본 {len(original)}개와 동일: {len(reparsed) == len(original)})")


if __name__ == "__main__":
    main()
