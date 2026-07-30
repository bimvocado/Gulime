"""
data/parsed_conditions.json + data/raw_products.json 을 Neon PostgreSQL의
products / product_conditions 테이블에 적재한다.

Spring 프로젝트와 분리된 독립 Python 스크립트.

사용법:
    python scripts/load_to_db.py --dry-run   # 매칭 결과만 확인 (DB 연결 없음)
    python scripts/load_to_db.py             # 실제 적재

필요 조건:
    - 프로젝트 루트 .env 에 DB_URL / DB_USERNAME / DB_PASSWORD 설정
    - pip install -r scripts/requirements.txt  (psycopg2-binary 포함)

=== 확정된 처리 ===
    1. product_id = raw_products.json의 fin_co_no + fin_prdt_cd 조합
       (collect_and_merge.py의 조인 키와 동일한 방식)
    2. parsed_conditions.json <-> raw_products.json 매칭: (product_name, bank_name) 완전 일치
    3. product_type: raw_products.json의 "deposit" -> DEPOSIT, "saving" -> SAVINGS
       (PARKING 분류 기준이 원본 데이터에 없어 사용하지 않음)
    4. is_verified: 전부 False
    5. NOT NULL 컬럼 기본값 치환: resource=null -> "NONE", rate_bonus=null -> 0
    6. 이미 DB에 있는 product_id는 skip (중복 insert 방지)

=== 하람 확인 후 확정 (구현 완료) ===
    - resolve_term_fields: raw_products.json의 options[]를 save_trm(만기)별로 그룹핑해
      만기마다 별도의 Product row를 만든다 (동일 만기에 rsrv_type 등으로 옵션이 중복되면
      intr_rate2가 더 높은 쪽을 대표값으로 선택). 만기가 2개 이상이면 product_id에
      "_{개월}M" 접미사를 붙여 별도 상품으로 분리한다.
    - split_tiers_to_rows: tiers를 전부 product_condition 행으로 저장한다
      (한도/조건 선택은 엔진이 런타임에 수행).
    - resolve_selection_type: tiers 길이가 2 이상이면 TIERED, 그 외에 조건이
      selectable=True이고 상품 최상위 selection_rule.max_select가 있으면 CHOICE(N중M선택),
      나머지는 SINGLE.
    - hard_requirement, selectable, selection_rule(상품 최상위)/max_select는
      스키마에 컬럼이 추가되어 각각 product_conditions/products row에 포함해 저장한다.
"""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlparse, urlunparse, quote

from dotenv import load_dotenv
import os

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"
RAW_PRODUCTS_PATH = DATA_DIR / "raw_products.json"
PARSED_CONDITIONS_PATH = DATA_DIR / "parsed_conditions.json"

# raw_products.json 최상위 키 -> Product.product_type
# PARKING은 원본 데이터에 분류 기준이 없어 사용하지 않는다 (product_type 매핑 확정 사항 #3).
PRODUCT_TYPE_MAP = {"deposit": "DEPOSIT", "saving": "SAVINGS"}


@dataclass
class MatchedProduct:
    product_id: str
    bank_name: str
    product_name: str
    ptype: str  # "deposit" | "saving"
    raw_product: dict
    parsed_entry: dict


# ---------------------------------------------------------------------------
# 로드 & 매칭 (확정)
# ---------------------------------------------------------------------------

def load_raw_products() -> dict[tuple[str, str], tuple[str, dict]]:
    """(product_name, bank_name) -> (ptype, raw_product) 매핑."""
    with open(RAW_PRODUCTS_PATH, "r", encoding="utf-8") as f:
        raw = json.load(f)

    index: dict[tuple[str, str], tuple[str, dict]] = {}
    duplicate_keys: list[tuple[str, str]] = []

    for ptype, items in raw.items():
        for item in items:
            key = (item.get("fin_prdt_nm"), item.get("kor_co_nm"))
            if key in index:
                duplicate_keys.append(key)
            index[key] = (ptype, item)

    if duplicate_keys:
        print(f"[경고] raw_products.json 내 (product_name, bank_name) 중복 {len(duplicate_keys)}건 "
              f"-> 마지막 항목으로 덮어씀")
        for key in duplicate_keys:
            print(f"  - {key[0]} ({key[1]})")

    return index


def load_parsed_conditions() -> list[dict]:
    with open(PARSED_CONDITIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def build_product_id(raw_product: dict) -> str:
    """collect_and_merge.py와 동일하게 fin_co_no + fin_prdt_cd 조합으로 고유 ID를 만든다."""
    fin_co_no = raw_product.get("fin_co_no")
    fin_prdt_cd = raw_product.get("fin_prdt_cd")
    if not fin_co_no or not fin_prdt_cd:
        raise ValueError(f"fin_co_no/fin_prdt_cd 누락: {raw_product.get('fin_prdt_nm')}")
    return f"{fin_co_no}_{fin_prdt_cd}"


def resolve_product_type(ptype: str) -> str:
    try:
        return PRODUCT_TYPE_MAP[ptype]
    except KeyError:
        raise ValueError(f"알 수 없는 상품 유형: {ptype!r} (PARKING 분류 기준 미정, deposit/saving만 지원)")


def match_products(
    raw_index: dict[tuple[str, str], tuple[str, dict]],
    parsed_entries: list[dict],
) -> tuple[list[MatchedProduct], list[dict]]:
    """parsed_conditions.json 각 항목을 raw_products.json과 (product_name, bank_name)로 매칭한다."""
    matched: list[MatchedProduct] = []
    unmatched: list[dict] = []

    for entry in parsed_entries:
        key = (entry.get("product_name"), entry.get("bank_name"))
        found = raw_index.get(key)
        if found is None:
            unmatched.append(entry)
            continue

        ptype, raw_product = found
        matched.append(
            MatchedProduct(
                product_id=build_product_id(raw_product),
                bank_name=entry.get("bank_name"),
                product_name=entry.get("product_name"),
                ptype=ptype,
                raw_product=raw_product,
                parsed_entry=entry,
            )
        )

    return matched, unmatched


# ---------------------------------------------------------------------------
# NOT NULL 기본값 치환 (확정 #5)
# ---------------------------------------------------------------------------

def normalize_resource(resource: str | None) -> str:
    return resource if resource is not None else "NONE"


def normalize_rate_bonus(rate_bonus) -> Decimal:
    if rate_bonus is None:
        return Decimal("0")
    return Decimal(str(rate_bonus))


# ---------------------------------------------------------------------------
# 하람 확인 후 확정된 산출 규칙
# ---------------------------------------------------------------------------

def resolve_term_fields(raw_product: dict) -> list[dict] | None:
    """
    raw_product["options"]를 만기(save_trm)별로 그룹핑해 만기마다 하나씩
    {"period_months", "base_rate", "max_rate", "max_limit"} 딕셔너리를 만든다.

    동일 만기에 옵션이 여럿(예: 자유적립식/정액적립식)이면 우대금리(intr_rate2)가
    더 높은 쪽을 그 만기의 대표값으로 선택한다.

    반환: 만기 개월수 오름차순 리스트. 만기가 여러 개면 build_product_rows에서
    만기마다 별도 상품(product_id 분리)으로 취급한다. 유효한 옵션이 하나도 없으면 None.
    """
    options = raw_product.get("options") or []
    max_limit = raw_product.get("max_limit")

    by_term: dict[int, dict] = {}
    for opt in options:
        try:
            months = int(opt.get("save_trm"))
        except (TypeError, ValueError):
            continue

        rate = opt.get("intr_rate")
        rate2 = opt.get("intr_rate2")
        if rate is None or rate2 is None:
            continue

        existing = by_term.get(months)
        if existing is None or rate2 > existing["intr_rate2"]:
            by_term[months] = {"intr_rate": rate, "intr_rate2": rate2}

    if not by_term:
        return None

    return [
        {
            "period_months": months,
            "base_rate": Decimal(str(vals["intr_rate"])),
            "max_rate": Decimal(str(vals["intr_rate2"])),
            "max_limit": max_limit,
        }
        for months, vals in sorted(by_term.items())
    ]


def resolve_selection_type(condition: dict, selection_rule: dict | None) -> str | None:
    """
    ProductCondition.selection_type 산출 규칙 (하람 확인):
      - tiers 길이가 2개 이상이면 TIERED (계단식)
      - (TIERED가 아니면서) selectable=True이고 상품 최상위 selection_rule.max_select가
        있으면 CHOICE (N중M선택)
      - 그 외는 SINGLE
    """
    tiers = condition.get("tiers") or []
    if len(tiers) >= 2:
        return "TIERED"

    if condition.get("selectable") and selection_rule and selection_rule.get("max_select"):
        return "CHOICE"

    return "SINGLE"


def split_tiers_to_rows(condition: dict) -> list[dict] | None:
    """
    tiers를 전부 product_condition 행으로 저장한다 (어떤 tier를 적용할지는
    엔진이 런타임에 선택). tiers가 없으면 None을 반환해 해당 조건은 skip 된다.
    """
    tiers = condition.get("tiers")
    if not tiers:
        return None

    return [{"threshold": tier.get("threshold"), "rate_bonus": tier.get("rate_bonus")} for tier in tiers]


def build_product_rows(matched: MatchedProduct) -> list[dict] | None:
    """products 테이블 insert용 row 목록.

    resolve_term_fields가 만기별로 나눠준 값 각각을 별도 상품 row로 만든다.
    만기가 2개 이상이면 base product_id에 "_{개월}M" 접미사를 붙여 상품을 분리하고,
    만기가 1개뿐이면 기존 product_id를 그대로 사용한다 (하위 호환).
    resolve_term_fields가 None이면 None을 반환해 skip 된다.
    """
    term_fields_list = resolve_term_fields(matched.raw_product)
    if term_fields_list is None:
        return None

    parsed = matched.parsed_entry.get("parsed") or {}
    selection_rule = parsed.get("selection_rule") or {}
    multi_term = len(term_fields_list) > 1

    rows = []
    for term_fields in term_fields_list:
        product_id = (
            f"{matched.product_id}_{term_fields['period_months']}M"
            if multi_term else matched.product_id
        )
        rows.append({
            "product_id": product_id,
            "bank_name": matched.bank_name,
            "product_name": matched.product_name,
            "product_type": resolve_product_type(matched.ptype),
            "base_rate": term_fields["base_rate"],
            "max_rate": term_fields["max_rate"],
            "max_limit": term_fields.get("max_limit"),
            "period_months": term_fields["period_months"],
            "is_verified": False,
            "selection_rule": "MAX_SELECT" if selection_rule.get("max_select") else None,
            "max_select": selection_rule.get("max_select"),
        })
    return rows


def build_condition_rows(product_id: str, condition: dict, selection_rule: dict | None) -> list[dict] | None:
    """product_conditions 테이블 insert용 row 목록. tiers가 없는 등 산출 불가하면 None."""
    selection_type = resolve_selection_type(condition, selection_rule)
    tiers = split_tiers_to_rows(condition)
    if selection_type is None or tiers is None:
        return None

    rows = []
    for tier_index, tier in enumerate(tiers):
        selectable_value = condition.get("selectable")
        selectable = bool(selectable_value)
        rows.append({
            "product_id": product_id,
            "period_months": condition.get("period_months"),
            "required_months": condition.get("required_months"),
            "selection_type": selection_type,
            "condition_name": condition.get("condition_name"),
            "type": condition["type"],
            "resource": normalize_resource(condition.get("resource")),
            "threshold": tier.get("threshold"),
            "rate_bonus": normalize_rate_bonus(tier.get("rate_bonus")),
            "payout_type": condition.get("payout"),
            "hard_requirement": bool(condition.get("hard_requirement", False)),
            "selectable": selectable,
            "selection_group": f"{product_id}_SELECTION" if selectable else None,
            "tier_group": f"{product_id}_{condition.get('condition_name', 'CONDITION')}_TIER"
                          if len(tiers) > 1 else None,
            "exclusive_group": condition.get("exclusive_group"),
            "branch": condition.get("branch"),
            "parse_status": condition.get("parse_status"),
            "source_text": condition.get("source_text"),
        })
    return rows


# ---------------------------------------------------------------------------
# DB 연결 (확정 #6) - .env 사용, psycopg2는 실제 적재 시에만 필요하므로 지연 import
# ---------------------------------------------------------------------------

def get_db_dsn() -> str:
    """.env의 DB_URL(jdbc 형식) + DB_USERNAME/DB_PASSWORD로 libpq DSN을 만든다.
    DSN/비밀번호는 어디에도 출력하지 않는다."""
    load_dotenv(dotenv_path=ROOT / ".env")
    db_url = os.getenv("DB_URL")
    db_user = os.getenv("DB_USERNAME")
    db_password = os.getenv("DB_PASSWORD")
    if not (db_url and db_user and db_password):
        raise RuntimeError(f"{ROOT / '.env'} 에서 DB_URL/DB_USERNAME/DB_PASSWORD 를 찾을 수 없습니다.")

    if db_url.startswith("jdbc:"):
        db_url = db_url[len("jdbc:"):]

    parsed = urlparse(db_url)
    netloc = f"{quote(db_user, safe='')}:{quote(db_password, safe='')}@{parsed.netloc}"
    return urlunparse(parsed._replace(netloc=netloc))


def get_connection():
    import psycopg2  # 지연 import: --dry-run에서는 설치 불필요
    return psycopg2.connect(get_db_dsn())


def get_existing_product_ids(cursor, product_ids: list[str]) -> set[str]:
    if not product_ids:
        return set()
    cursor.execute("SELECT product_id FROM products WHERE product_id = ANY(%s)", (product_ids,))
    return {row[0] for row in cursor.fetchall()}


def insert_product(cursor, product_row: dict) -> None:
    cursor.execute(
        """
        INSERT INTO products
            (product_id, bank_name, product_name, product_type, base_rate, max_rate,
             max_limit, period_months, is_verified, selection_rule, max_select)
        VALUES
            (%(product_id)s, %(bank_name)s, %(product_name)s, %(product_type)s, %(base_rate)s,
             %(max_rate)s, %(max_limit)s, %(period_months)s, %(is_verified)s,
             %(selection_rule)s, %(max_select)s)
        """,
        product_row,
    )


def insert_condition(cursor, condition_row: dict) -> None:
    cursor.execute(
        """
        INSERT INTO product_conditions
            (product_id, period_months, required_months, selection_type, condition_name,
             type, resource, threshold, rate_bonus, payout_type, hard_requirement,
             selectable, selection_group, tier_group, exclusive_group, branch,
             parse_status, source_text)
        VALUES
            (%(product_id)s, %(period_months)s, %(required_months)s, %(selection_type)s,
             %(condition_name)s, %(type)s, %(resource)s, %(threshold)s, %(rate_bonus)s,
             %(payout_type)s, %(hard_requirement)s, %(selectable)s, %(selection_group)s,
             %(tier_group)s, %(exclusive_group)s, %(branch)s, %(parse_status)s, %(source_text)s)
        """,
        condition_row,
    )


# ---------------------------------------------------------------------------
# 실제 적재 (확정 #6 dedup 포함) - TODO 미구현 상품/조건은 자동 skip
# ---------------------------------------------------------------------------

def run_load(matched: list[MatchedProduct], conn) -> None:
    with conn.cursor() as cur:
        # 만기별로 상품이 분리될 수 있어(build_product_rows), 먼저 전체 product row를
        # 만든 뒤 실제 insert될 product_id 전체를 대상으로 기존 존재 여부를 조회한다.
        per_matched_rows: list[tuple[MatchedProduct, list[dict]]] = []
        skipped_todo = 0

        for m in matched:
            product_rows = build_product_rows(m)
            if product_rows is None:
                skipped_todo += 1
                print(f"  [TODO 미구현으로 skip] {m.product_name} ({m.bank_name})")
                continue
            per_matched_rows.append((m, product_rows))

        all_product_ids = [row["product_id"] for _, rows in per_matched_rows for row in rows]
        existing = get_existing_product_ids(cur, all_product_ids)

        inserted_products = 0
        skipped_existing = 0
        inserted_conditions = 0

        for m, product_rows in per_matched_rows:
            parsed = m.parsed_entry.get("parsed") or {}
            selection_rule = parsed.get("selection_rule")
            conditions = parsed.get("conditions", [])

            for product_row in product_rows:
                product_id = product_row["product_id"]
                if product_id in existing:
                    skipped_existing += 1
                    continue

                insert_product(cur, product_row)
                inserted_products += 1

                for condition in conditions:
                    rows = build_condition_rows(product_id, condition, selection_rule)
                    if rows is None:
                        continue
                    for row in rows:
                        insert_condition(cur, row)
                        inserted_conditions += 1

        conn.commit()

        print("\n=== 적재 결과 ===")
        print(f"신규 insert된 상품: {inserted_products}건")
        print(f"이미 존재해 skip: {skipped_existing}건")
        print(f"TODO 미구현으로 skip: {skipped_todo}건")
        print(f"insert된 조건: {inserted_conditions}건")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dry-run", action="store_true",
        help="DB에 연결하지 않고 (product_name, bank_name) 매칭 결과 건수만 출력",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    raw_index = load_raw_products()
    parsed_entries = load_parsed_conditions()
    matched, unmatched = match_products(raw_index, parsed_entries)

    print(f"parsed_conditions.json 총 {len(parsed_entries)}건")
    print(f"raw_products.json 매칭 성공: {len(matched)}건")
    print(f"매칭 실패: {len(unmatched)}건")
    if unmatched:
        print("매칭 실패 목록 (raw_products.json에서 동일한 product_name/bank_name을 찾지 못함):")
        for e in unmatched:
            print(f"  - {e.get('product_name')} ({e.get('bank_name')})")

    if args.dry_run:
        print("\n[dry-run] DB에 연결하지 않았습니다. 실제 적재는 --dry-run 없이 실행하세요.")
        return

    conn = get_connection()
    try:
        run_load(matched, conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
