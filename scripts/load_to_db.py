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

=== 아직 확정 안 됨 (하람 확인 중) - TODO 함수 골격만 존재, pass 상태 ===
    - resolve_term_fields: 대표 만기(period_months) 및 그에 따른 base_rate/max_rate/max_limit 선택 규칙
      raw_products.json의 options[]는 상품 하나에 여러 만기(save_trm)의 금리를 담고 있어
      Product 테이블의 단일 period_months/base_rate/max_rate 로 어떻게 대표값을 뽑을지 미정.
    - split_tiers_to_rows: 계단식 조건(tiers 길이 2개 이상, 17건)을 product_condition
      여러 행으로 분리하는 방식 미정.
    - resolve_selection_type: ProductCondition.selection_type(TIERED/CHOICE/SINGLE)을
      parsed_conditions.json의 tiers 길이 / selectable / 상품 최상위 selection_rule.max_select
      로부터 어떻게 산출할지 미정.
    - hard_requirement, selection_rule(상품 최상위) 저장 여부: 현재 스키마에 대응 컬럼이 없어
      컬럼 추가 여부까지 포함해 미정. 컬럼이 추가되기 전까지는 그냥 버려진다.

    위 TODO가 채워지지 않은 상품/조건은 실제 적재 시 자동으로 skip 되고, 그 사유가 출력된다.
    TODO 함수들을 구현하면 이 스크립트를 --dry-run 없이 그대로 실행해 적재할 수 있다.
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
# TODO (하람 확인 중) - 함수 골격만 존재, 구현 전까지 None을 반환해 자동 skip 된다.
# ---------------------------------------------------------------------------

def resolve_term_fields(raw_product: dict) -> dict | None:
    """
    TODO(하람 확인 중): 대표 만기(period_months) 선택 규칙 미확정.

    raw_product["options"]는 상품 하나에 여러 만기(save_trm)별 금리(intr_rate/intr_rate2)를
    담고 있는데, Product 테이블은 상품당 period_months/base_rate/max_rate가 각각 1개뿐이라
    어느 만기를 대표값으로 쓸지 결정이 필요하다.

    구현 시 반환 형식 예시:
        {"period_months": int, "base_rate": Decimal, "max_rate": Decimal, "max_limit": int | None}
    """
    # TODO: 하람 답변 후 구현
    pass


def resolve_selection_type(condition: dict, selection_rule: dict | None) -> str | None:
    """
    TODO(하람 확인 중): ProductCondition.selection_type(TIERED/CHOICE/SINGLE) 산출 규칙 미확정.

    parsed_conditions.json에는 selection_type이 직접 없고 대신
    condition["tiers"](길이), condition["selectable"], 상품 최상위 selection_rule.max_select 가 있다.
    """
    # TODO: 하람 답변 후 구현
    pass


def split_tiers_to_rows(condition: dict) -> list[dict] | None:
    """
    TODO(하람 확인 중): 계단식 조건(tiers 길이 2개 이상, 96개 상품 중 17건)을
    product_condition 여러 행으로 분리하는 방식 미확정.

    구현 시 반환 형식 예시 (tier 개수만큼): [{"threshold": ..., "rate_bonus": ...}, ...]
    """
    # TODO: 하람 답변 후 구현
    pass


def build_product_row(matched: MatchedProduct) -> dict | None:
    """products 테이블 insert용 row. TODO(resolve_term_fields) 미구현이면 None."""
    term_fields = resolve_term_fields(matched.raw_product)
    if term_fields is None:
        return None

    parsed = matched.parsed_entry.get("parsed") or {}
    selection_rule = parsed.get("selection_rule") or {}
    return {
        "product_id": matched.product_id,
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
    }


def build_condition_rows(product_id: str, condition: dict, selection_rule: dict | None) -> list[dict] | None:
    """product_conditions 테이블 insert용 row 목록. TODO 미구현이면 None."""
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
        existing = get_existing_product_ids(cur, [m.product_id for m in matched])

        inserted_products = 0
        skipped_existing = 0
        skipped_todo = 0
        inserted_conditions = 0

        for m in matched:
            if m.product_id in existing:
                skipped_existing += 1
                continue

            product_row = build_product_row(m)
            if product_row is None:
                skipped_todo += 1
                print(f"  [TODO 미구현으로 skip] {m.product_name} ({m.bank_name})")
                continue

            insert_product(cur, product_row)
            inserted_products += 1

            parsed = m.parsed_entry.get("parsed") or {}
            selection_rule = parsed.get("selection_rule")
            for condition in parsed.get("conditions", []):
                rows = build_condition_rows(m.product_id, condition, selection_rule)
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

    print("\nTODO 항목(대표 만기 선택 / tier 분리 / selection_type 산출)이 아직 확정되지 않았습니다.")
    print("해당 상품/조건은 자동으로 skip 되며, 하람 확인 후 TODO 함수들을 구현하면 그대로 적재됩니다.")

    conn = get_connection()
    try:
        run_load(matched, conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
