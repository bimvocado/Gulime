"""
금융감독원 금융상품 오픈API에서 예적금 상품을 수집하고
data/validation_set.json(팀원 수기 검증셋)과 병합한다.

Spring 프로젝트와 분리된 독립 Python 스크립트.

사용법:
    python scripts/collect_and_merge.py

필요 조건:
    - 프로젝트 루트 .env 에 FSS_API_KEY 설정
    - pip install requests python-dotenv
"""
from __future__ import annotations

import argparse
import json
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import requests
from dotenv import load_dotenv
import os

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"
RAW_PRODUCTS_PATH = DATA_DIR / "raw_products.json"
VALIDATION_SET_PATH = DATA_DIR / "validation_set.json"
MERGED_OUTPUT_PATH = DATA_DIR / "merged_validation.json"

DEPOSIT_URL = "https://finlife.fss.or.kr/finlifeapi/depositProductsSearch.json"
SAVING_URL = "https://finlife.fss.or.kr/finlifeapi/savingProductsSearch.json"
TOP_FIN_GRP_NO = "020000"
REQUEST_DELAY_SEC = 0.2
REQUEST_TIMEOUT_SEC = 30
RETRY_BACKOFF_SEC = [2, 4, 8]  # 요청 실패 시 재시도 전 대기 시간 (3회 재시도)

PRODUCT_TYPES = [
    ("deposit", DEPOSIT_URL, "정기예금"),
    ("saving", SAVING_URL, "적금"),
]


class FetchFailedError(Exception):
    """재시도를 모두 소진해도 요청이 실패했을 때 발생."""


def load_api_key() -> str:
    load_dotenv(dotenv_path=ROOT / ".env")
    api_key = os.getenv("FSS_API_KEY")
    if not api_key:
        raise RuntimeError(
            f"{ROOT / '.env'} 에서 FSS_API_KEY 를 찾을 수 없습니다. "
            "FSS_API_KEY=발급받은키 형식으로 설정해주세요."
        )
    return api_key


def request_with_retry(url: str, params: dict, label: str) -> requests.Response:
    """네트워크 실패 시 2초, 4초, 8초 백오프로 최대 3회 재시도한다."""
    max_attempts = len(RETRY_BACKOFF_SEC) + 1
    last_exc: Exception | None = None

    for attempt in range(1, max_attempts + 1):
        try:
            resp = requests.get(url, params=params, timeout=REQUEST_TIMEOUT_SEC)
            resp.raise_for_status()
            return resp
        except requests.RequestException as exc:
            last_exc = exc
            if attempt < max_attempts:
                backoff = RETRY_BACKOFF_SEC[attempt - 1]
                print(f"  [{label}] 요청 실패 ({attempt}/{max_attempts}): {exc} "
                      f"-> {backoff}초 후 재시도")
                time.sleep(backoff)
            else:
                print(f"  [{label}] 요청 {max_attempts}회 모두 실패: {exc}")

    raise FetchFailedError(f"[{label}] 재시도 소진: {last_exc}") from last_exc


def fetch_all_pages(url: str, api_key: str, label: str) -> tuple[list[dict], list[dict]]:
    """주어진 API에서 전체 페이지를 순회하며 baseList/optionList를 모두 수집한다."""
    base_list: list[dict] = []
    option_list: list[dict] = []

    page_no = 1
    max_page_no = 1

    while page_no <= max_page_no:
        params = {"auth": api_key, "topFinGrpNo": TOP_FIN_GRP_NO, "pageNo": page_no}
        resp = request_with_retry(url, params, label)
        payload = resp.json()

        result = payload.get("result", {})
        err_cd = result.get("err_cd")
        if err_cd not in (None, "000"):
            raise RuntimeError(
                f"[{label}] FSS API 오류 (err_cd={err_cd}): {result.get('err_msg')}"
            )

        base_list.extend(result.get("baseList", []))
        option_list.extend(result.get("optionList", []))

        max_page_no = int(result.get("max_page_no", page_no))
        print(f"  [{label}] page {page_no}/{max_page_no} 수집 완료 "
              f"(누적 base={len(base_list)}, option={len(option_list)})")

        page_no += 1
        if page_no <= max_page_no:
            time.sleep(REQUEST_DELAY_SEC)

    return base_list, option_list


def join_base_and_options(base_list: list[dict], option_list: list[dict]) -> list[dict]:
    """fin_co_no + fin_prdt_cd 기준으로 baseList와 optionList를 조인한다."""
    options_by_key: dict[tuple[str, str], list[dict]] = {}
    for opt in option_list:
        key = (opt.get("fin_co_no"), opt.get("fin_prdt_cd"))
        options_by_key.setdefault(key, []).append(opt)

    joined = []
    for base in base_list:
        key = (base.get("fin_co_no"), base.get("fin_prdt_cd"))
        product = dict(base)
        product["options"] = options_by_key.get(key, [])
        joined.append(product)
    return joined


def load_existing_raw_products() -> dict[str, list[dict]]:
    if not RAW_PRODUCTS_PATH.exists():
        return {}
    try:
        with open(RAW_PRODUCTS_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError:
        return {}


def prompt_yes_no(question: str, default: bool = False) -> bool:
    try:
        answer = input(question).strip().lower()
    except EOFError:
        print("(비대화형 환경이라 응답을 받을 수 없어 새로 수집합니다)")
        return default
    return answer in ("y", "yes")


def save_raw_products(products_by_type: dict[str, list[dict]]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(RAW_PRODUCTS_PATH, "w", encoding="utf-8") as f:
        json.dump(products_by_type, f, ensure_ascii=False, indent=2)
    print(f"원본 수집 데이터 저장: {RAW_PRODUCTS_PATH}")


def collect_products(api_key: str, args: argparse.Namespace) -> dict[str, list[dict]]:
    existing_raw = load_existing_raw_products()
    products_by_type: dict[str, list[dict]] = dict(existing_raw)

    for key, url, label in PRODUCT_TYPES:
        cached = existing_raw.get(key)
        skip_flag = getattr(args, f"skip_{key}")

        if skip_flag:
            if not cached:
                raise RuntimeError(
                    f"--skip-{key} 옵션을 사용했지만 {RAW_PRODUCTS_PATH}에 "
                    f"캐시된 {label} 데이터가 없습니다."
                )
            print(f"[{label}] --skip-{key} 옵션에 따라 기존 데이터 재사용 ({len(cached)}건)")
            products_by_type[key] = cached
            save_raw_products(products_by_type)
            continue

        if cached and prompt_yes_no(
            f"{RAW_PRODUCTS_PATH}에 기존 {label} 데이터 {len(cached)}건이 있습니다. "
            f"재사용할까요? (y/n): "
        ):
            products_by_type[key] = cached
            save_raw_products(products_by_type)
            continue

        print(f"{label} 수집 시작...")
        try:
            base, option = fetch_all_pages(url, api_key, label)
            products_by_type[key] = join_base_and_options(base, option)
        except (FetchFailedError, RuntimeError) as exc:
            print(f"[{label}] 수집 실패, 이 상품유형은 건너뜁니다: {exc}")
            products_by_type[key] = cached if cached else []

        # 예금/적금 중 하나가 실패해도 이미 성공한 유형은 즉시 디스크에 남긴다.
        save_raw_products(products_by_type)

    return products_by_type


_NORMALIZE_PATTERN = re.compile(r"[^0-9A-Za-z가-힣]")


def normalize(name: str | None) -> str:
    """공백/특수문자를 제거하고 소문자로 통일해 비교 가능한 형태로 만든다."""
    if not name:
        return ""
    return _NORMALIZE_PATTERN.sub("", name).lower()


# 검증셋 작성자가 쓰는 통칭과 FSS API가 반환하는 공식 은행명이 다른 경우의 매핑.
# (검증셋에서 쓰는 이름 -> API의 kor_co_nm)
BANK_ALIASES_RAW: dict[str, str] = {
    "KB국민은행": "국민은행",
    "IBK기업은행": "중소기업은행",
    "SC제일은행": "한국스탠다드차타드은행",
    "SH수협은행": "수협은행",
}

CORPORATE_DESIGNATORS = ["주식회사", "㈜"]


def _strip_corporate_designators(name: str) -> str:
    cleaned = name
    for token in CORPORATE_DESIGNATORS:
        cleaned = cleaned.replace(token, "")
    return cleaned


def _alias_key(name: str) -> str:
    return normalize(_strip_corporate_designators(name))


BANK_ALIASES: dict[str, str] = {
    _alias_key(k): _alias_key(v) for k, v in BANK_ALIASES_RAW.items()
}


def canonical_bank_name(name: str | None) -> str:
    """법인격 표기(주식회사/㈜)를 제거하고 별칭 테이블로 표준 은행명으로 맞춘다."""
    if not name:
        return ""
    key = _alias_key(name)
    return BANK_ALIASES.get(key, key)


@dataclass
class MatchResult:
    validation_entry: dict
    matched_product: dict | None = None
    product_type: str | None = None
    match_type: str | None = None  # "full" | "partial" | None


def match_validation_entries(
    validation_entries: list[dict], products_by_type: dict[str, list[dict]]
) -> list[MatchResult]:
    all_products: list[tuple[str, dict]] = []
    for ptype, products in products_by_type.items():
        for p in products:
            all_products.append((ptype, p))

    results: list[MatchResult] = []
    for entry in validation_entries:
        vn = normalize(entry.get("product_name"))
        vb = canonical_bank_name(entry.get("bank_name"))

        full_matches = [
            (ptype, p) for ptype, p in all_products if normalize(p.get("fin_prdt_nm")) == vn
        ]

        if full_matches:
            candidates = full_matches
            match_type = "full"
        else:
            candidates = [
                (ptype, p)
                for ptype, p in all_products
                if vn
                and (
                    vn in normalize(p.get("fin_prdt_nm"))
                    or normalize(p.get("fin_prdt_nm")) in vn
                )
            ]
            match_type = "partial" if candidates else None

        if not candidates:
            results.append(MatchResult(validation_entry=entry))
            continue

        bank_matches = [(pt, p) for pt, p in candidates if canonical_bank_name(p.get("kor_co_nm")) == vb]
        chosen_type, chosen_product = bank_matches[0] if bank_matches else candidates[0]

        results.append(
            MatchResult(
                validation_entry=entry,
                matched_product=chosen_product,
                product_type=chosen_type,
                match_type=match_type,
            )
        )

    return results


def build_merged_output(match_results: list[MatchResult]) -> list[dict]:
    merged = []
    for r in match_results:
        if r.matched_product is None:
            continue
        p = r.matched_product
        merged.append(
            {
                "product_name": p.get("fin_prdt_nm"),
                "bank_name": p.get("kor_co_nm"),
                "spcl_cnd": p.get("spcl_cnd"),
                "join_member": p.get("join_member"),
                "join_deny": p.get("join_deny"),
                "expected": r.validation_entry,
            }
        )
    return merged


def print_report(
    products_by_type: dict[str, list[dict]],
    validation_entries: list[dict],
    match_results: list[MatchResult],
) -> None:
    deposit_count = len(products_by_type["deposit"])
    saving_count = len(products_by_type["saving"])

    print("\n=== 수집 결과 ===")
    print(f"정기예금: {deposit_count}개")
    print(f"적금: {saving_count}개")
    print(f"합계: {deposit_count + saving_count}개")

    succeeded = [r for r in match_results if r.matched_product is not None]
    failed = [r for r in match_results if r.matched_product is None]
    full = [r for r in succeeded if r.match_type == "full"]
    partial = [r for r in succeeded if r.match_type == "partial"]

    print(f"\n=== 검증셋 매칭 결과 (전체 {len(validation_entries)}개) ===")
    print(f"매칭 성공: {len(succeeded)}개 (완전일치 {len(full)}개, 부분일치 {len(partial)}개)")
    print(f"매칭 실패: {len(failed)}개")

    if failed:
        print("\n실패 목록 (API에 없는 상품):")
        for r in failed:
            name = r.validation_entry.get("product_name")
            bank = r.validation_entry.get("bank_name")
            print(f"  - {name} ({bank})")

    all_products_flat = [p for products in products_by_type.values() for p in products]
    kb_canonical = canonical_bank_name("KB국민은행")
    kb_total = sum(1 for p in all_products_flat if canonical_bank_name(p.get("kor_co_nm")) == kb_canonical)
    kb_validation = sum(
        1 for e in validation_entries if canonical_bank_name(e.get("bank_name")) == kb_canonical
    )

    print("\n=== KB국민은행 상품 현황 ===")
    print(f"전체 수집 데이터 중 KB국민은행 상품: {kb_total}개")
    print(f"검증셋 중 KB국민은행 상품: {kb_validation}개")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--skip-deposit", action="store_true",
        help=f"정기예금 수집을 건너뛰고 {RAW_PRODUCTS_PATH}의 기존 데이터를 사용",
    )
    parser.add_argument(
        "--skip-saving", action="store_true",
        help=f"적금 수집을 건너뛰고 {RAW_PRODUCTS_PATH}의 기존 데이터를 사용",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    api_key = load_api_key()

    products_by_type = collect_products(api_key, args)

    with open(VALIDATION_SET_PATH, "r", encoding="utf-8") as f:
        validation_entries = json.load(f)

    match_results = match_validation_entries(validation_entries, products_by_type)
    merged = build_merged_output(match_results)

    with open(MERGED_OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)
    print(f"\n병합 결과 저장: {MERGED_OUTPUT_PATH} ({len(merged)}건)")

    print_report(products_by_type, validation_entries, match_results)


if __name__ == "__main__":
    main()
