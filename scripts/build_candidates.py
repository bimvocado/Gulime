"""
data/raw_products.json 을 검증셋 작성용 후보 목록(Markdown)으로 변환한다.
data/merged_validation.json 에 이미 있는 상품은 [작성완료]로 표시해 중복 작업을 막는다.

사용법:
    python scripts/build_candidates.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import collect_and_merge as cm

RAW_PRODUCTS_PATH = cm.RAW_PRODUCTS_PATH
MERGED_OUTPUT_PATH = cm.MERGED_OUTPUT_PATH
CANDIDATES_PATH = cm.DATA_DIR / "candidates.md"

TYPE_LABELS = {"deposit": "예금", "saving": "적금"}

BUCKET_TITLES = {
    1: "1. 국민은행 상품",
    2: "2. 카드/급여 조건 포함",
    3: "3. 복합 조건 (%p 2개 이상)",
    4: "4. 조건 없음 (해당사항 없음 / 빈 값)",
    5: "5. 나머지",
}


def load_json(path: Path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def completed_keys(merged: list[dict]) -> set[tuple[str, str]]:
    return {
        (cm.canonical_bank_name(e.get("bank_name")), cm.normalize(e.get("product_name")))
        for e in merged
    }


def classify(spcl_cnd: str, is_kb: bool) -> int:
    text = spcl_cnd or ""
    if is_kb:
        return 1
    if "카드" in text or "급여" in text:
        return 2
    if text.count("%p") >= 2:
        return 3
    if text.strip() in ("", "해당사항 없음"):
        return 4
    return 5


def main() -> None:
    raw = load_json(RAW_PRODUCTS_PATH)
    merged = load_json(MERGED_OUTPUT_PATH) if MERGED_OUTPUT_PATH.exists() else []
    done_keys = completed_keys(merged)
    kb_canonical = cm.canonical_bank_name("KB국민은행")

    items = []
    for ptype, products in raw.items():
        type_label = TYPE_LABELS.get(ptype, ptype)
        for p in products:
            name = p.get("fin_prdt_nm") or ""
            bank = p.get("kor_co_nm") or ""
            spcl_cnd = p.get("spcl_cnd") or ""
            is_kb = cm.canonical_bank_name(bank) == kb_canonical
            bucket = classify(spcl_cnd, is_kb)
            key = (cm.canonical_bank_name(bank), cm.normalize(name))
            items.append(
                {
                    "bucket": bucket,
                    "name": name,
                    "bank": bank,
                    "type_label": type_label,
                    "spcl_cnd": spcl_cnd,
                    "done": key in done_keys,
                }
            )

    items.sort(key=lambda it: (it["bucket"], it["bank"], it["name"]))

    total = len(items)
    done_total = sum(1 for it in items if it["done"])

    lines = [
        "# 검증셋 작성 후보 목록",
        "",
        f"raw_products.json 기준 총 {total}개 상품 중 {done_total}개는 "
        "merged_validation.json에 이미 있어 [작성완료]로 표시됩니다.",
        "",
    ]

    counter = 0
    current_bucket = None
    for it in items:
        if it["bucket"] != current_bucket:
            current_bucket = it["bucket"]
            bucket_count = sum(1 for x in items if x["bucket"] == current_bucket)
            lines.append(f"## {BUCKET_TITLES[current_bucket]} ({bucket_count}개)")
            lines.append("")

        counter += 1
        done_mark = " [작성완료]" if it["done"] else ""
        heading_name = " ".join(it["name"].split())
        heading_bank = " ".join(it["bank"].split())
        lines.append(f"### {counter}. {heading_name} — {heading_bank} ({it['type_label']}){done_mark}")
        lines.append("")
        lines.append("spcl_cnd:")
        lines.append("```")
        lines.append(it["spcl_cnd"] if it["spcl_cnd"] else "(없음)")
        lines.append("```")
        lines.append("")

    CANDIDATES_PATH.write_text("\n".join(lines), encoding="utf-8")

    print(f"저장 완료: {CANDIDATES_PATH}")
    print(f"총 {total}개, 작성완료 {done_total}개, 남은 작업 {total - done_total}개")
    for b in sorted(BUCKET_TITLES):
        cnt = sum(1 for x in items if x["bucket"] == b)
        done_cnt = sum(1 for x in items if x["bucket"] == b and x["done"])
        print(f"  {BUCKET_TITLES[b]}: {cnt}개 (작성완료 {done_cnt}개)")


if __name__ == "__main__":
    main()
