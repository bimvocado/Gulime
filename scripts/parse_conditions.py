"""
prompts/parsing_prompt_v2.txt(정식 버전) 를 사용해 data/raw_products.json 의
spcl_cnd(우대조건 원문)를 업스테이지 Solar LLM으로 구조화된 JSON으로 파싱한다.

정식 버전: parsing_prompt_v2.txt / parsed_conditions_v2.json (기본값)
롤백용:   parsing_prompt_v1.txt / parsed_conditions.json
         (--prompt parsing_prompt_v1.txt --output parsed_conditions.json 로 재현 가능)
자세한 채택 경위는 docs/parser-prompt-v2-adoption.md 참고.

Spring 프로젝트와 분리된 독립 Python 스크립트.

사용법:
    python scripts/parse_conditions.py            # 전체 상품 파싱 (v2 프롬프트, 기본값)
    python scripts/parse_conditions.py --limit 5   # 앞 5개만 테스트
    python scripts/parse_conditions.py --prompt parsing_prompt_v1.txt --output parsed_conditions.json
                                                    # v1 롤백 재현

필요 조건:
    - 프로젝트 루트 .env 에 UPSTAGE_API_KEY 설정
    - pip install -r scripts/requirements.txt

재개:
    - --output 으로 지정한 결과 파일에 이미 기록된 (product_name, bank_name) 항목은
      다시 호출하지 않고 건너뛴다. 중간에 끊겨도 그대로 재실행하면 이어서 진행된다.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import time
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from openai import OpenAI

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "data"
PROMPT_PATH = ROOT / "prompts" / "parsing_prompt_v2.txt"
RAW_PRODUCTS_PATH = DATA_DIR / "raw_products.json"
OUTPUT_PATH = DATA_DIR / "parsed_conditions_v2.json"

MODEL = "solar-pro3"
CALL_DELAY_SEC = 0.5
RETRY_BACKOFF_SEC = [2, 4, 8]  # 호출 실패 시 재시도 전 대기 시간 (3회 재시도)
SAVE_EVERY = 10

SKIP_SPCL_CND_VALUES = {"", "해당사항 없음", "해당무"}

_FENCE_PATTERN = re.compile(r"^```(?:json)?\s*\n?(.*?)\n?```$", re.DOTALL)


class LlmCallFailedError(Exception):
    """재시도를 모두 소진해도 LLM 호출이 실패했을 때 발생."""


def load_api_key() -> str:
    load_dotenv(dotenv_path=ROOT / ".env")
    api_key = os.getenv("UPSTAGE_API_KEY")
    if not api_key:
        raise RuntimeError(
            f"{ROOT / '.env'} 에서 UPSTAGE_API_KEY 를 찾을 수 없습니다. "
            "UPSTAGE_API_KEY=발급받은키 형식으로 설정해주세요."
        )
    return api_key


def load_products() -> list[dict]:
    with open(RAW_PRODUCTS_PATH, "r", encoding="utf-8") as f:
        raw = json.load(f)

    products = []
    for _ptype, items in raw.items():
        for p in items:
            products.append(
                {
                    "product_name": p.get("fin_prdt_nm") or "",
                    "bank_name": p.get("kor_co_nm") or "",
                    "spcl_cnd": p.get("spcl_cnd") or "",
                }
            )
    return products


def load_existing_results() -> dict[tuple[str, str], dict]:
    if not OUTPUT_PATH.exists():
        return {}
    try:
        with open(OUTPUT_PATH, "r", encoding="utf-8") as f:
            existing = json.load(f)
    except json.JSONDecodeError:
        return {}
    return {(e.get("product_name"), e.get("bank_name")): e for e in existing}


def save_results(results_by_key: dict[tuple[str, str], dict], ordered_keys: list[tuple[str, str]]) -> None:
    ordered = [results_by_key[k] for k in ordered_keys if k in results_by_key]
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(ordered, f, ensure_ascii=False, indent=2)


def should_skip_llm(spcl_cnd: str) -> bool:
    return spcl_cnd.strip() in SKIP_SPCL_CND_VALUES


def build_prompt(template: str, product_name: str, bank_name: str, spcl_cnd: str) -> str:
    return (
        template.replace("{product_name}", product_name)
        .replace("{bank_name}", bank_name)
        .replace("{spcl_cnd}", spcl_cnd)
    )


def extract_json_text(text: str) -> str:
    """```json ... ``` 코드블록으로 감싸져 오면 벗겨낸다."""
    stripped = text.strip()
    m = _FENCE_PATTERN.match(stripped)
    if m:
        return m.group(1).strip()
    return stripped


def parse_llm_json(text: str) -> Any:
    candidate = extract_json_text(text)
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        # 코드블록도 없고 앞뒤에 잡문이 섞여 온 경우, 첫 '{'~마지막 '}' 구간을 한 번 더 시도.
        start = candidate.find("{")
        end = candidate.rfind("}")
        if start != -1 and end != -1 and end > start:
            return json.loads(candidate[start : end + 1])
        raise


def call_llm_with_retry(client: OpenAI, prompt: str, label: str) -> str:
    max_attempts = len(RETRY_BACKOFF_SEC) + 1
    last_exc: Exception | None = None

    for attempt in range(1, max_attempts + 1):
        try:
            response = client.chat.completions.create(
                model=MODEL,
                messages=[{"role": "user", "content": prompt}],
                stream=False,
                temperature=0,
            )
            return response.choices[0].message.content or ""
        except Exception as exc:  # noqa: BLE001 - LLM SDK 예외를 폭넓게 재시도
            last_exc = exc
            if attempt < max_attempts:
                backoff = RETRY_BACKOFF_SEC[attempt - 1]
                print(f"  [{label}] 호출 실패 ({attempt}/{max_attempts}): {exc} -> {backoff}초 후 재시도")
                time.sleep(backoff)
            else:
                print(f"  [{label}] 호출 {max_attempts}회 모두 실패: {exc}")

    raise LlmCallFailedError(f"[{label}] 재시도 소진: {last_exc}") from last_exc


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--limit", type=int, default=None,
        help="처음 N개 상품만 처리 (테스트용, 예: --limit 5)",
    )
    parser.add_argument(
        "--prompt", type=str, default="parsing_prompt_v2.txt",
        help="prompts/ 아래 프롬프트 파일명 (기본값: parsing_prompt_v2.txt, 정식 버전)",
    )
    parser.add_argument(
        "--output", type=str, default="parsed_conditions_v2.json",
        help="data/ 아래 결과 파일명 (기본값: parsed_conditions_v2.json, 정식 버전)",
    )
    return parser.parse_args()


def main() -> None:
    global PROMPT_PATH, OUTPUT_PATH
    args = parse_args()
    PROMPT_PATH = ROOT / "prompts" / args.prompt
    OUTPUT_PATH = DATA_DIR / args.output

    api_key = load_api_key()
    client = OpenAI(api_key=api_key, base_url="https://api.upstage.ai/v1")

    template = PROMPT_PATH.read_text(encoding="utf-8")
    all_products = load_products()
    target_products = all_products[: args.limit] if args.limit is not None else all_products
    ordered_keys = [(p["product_name"], p["bank_name"]) for p in target_products]

    results_by_key = load_existing_results()

    total = len(target_products)
    processed_this_run = 0
    call_count_since_save = 0

    for idx, product in enumerate(target_products, start=1):
        key = (product["product_name"], product["bank_name"])
        label = f"{product['product_name']} ({product['bank_name']})"

        if key in results_by_key:
            print(f"[{idx}/{total}] (이미 처리됨, 건너뜀) {label}")
            continue

        spcl_cnd = product["spcl_cnd"]
        entry: dict[str, Any] = {
            "product_name": product["product_name"],
            "bank_name": product["bank_name"],
            "spcl_cnd": spcl_cnd,
        }

        if should_skip_llm(spcl_cnd):
            print(f"[{idx}/{total}] (조건 없음, LLM 호출 스킵) {label}")
            entry["parsed"] = {"selection_rule": None, "conditions": []}
        else:
            print(f"[{idx}/{total}] 파싱 중... {label}")
            prompt = build_prompt(template, product["product_name"], product["bank_name"], spcl_cnd)
            try:
                raw_response = call_llm_with_retry(client, prompt, label)
            except LlmCallFailedError as exc:
                entry["parsed"] = None
                entry["parse_error"] = f"LLM 호출 실패: {exc}"
            else:
                try:
                    entry["parsed"] = parse_llm_json(raw_response)
                except json.JSONDecodeError as exc:
                    entry["parsed"] = None
                    entry["parse_error"] = f"JSON 파싱 실패: {exc}"
                    entry["raw_response"] = raw_response
            finally:
                time.sleep(CALL_DELAY_SEC)

        results_by_key[key] = entry
        processed_this_run += 1
        call_count_since_save += 1

        if call_count_since_save >= SAVE_EVERY:
            save_results(results_by_key, ordered_keys)
            call_count_since_save = 0
            print(f"  (중간 저장 완료: {OUTPUT_PATH})")

    save_results(results_by_key, ordered_keys)

    final_entries = [results_by_key[k] for k in ordered_keys if k in results_by_key]
    succeeded = [e for e in final_entries if "parse_error" not in e]
    failed = [e for e in final_entries if "parse_error" in e]

    print(f"\n결과 저장: {OUTPUT_PATH}")
    print("\n=== 요약 ===")
    print(f"이번 실행에서 처리: {processed_this_run}개")
    print(f"전체 대상: {total}개 (성공 {len(succeeded)}개 / 실패 {len(failed)}개)")

    if failed:
        print("\n실패 목록:")
        for e in failed:
            print(f"  - {e['product_name']} ({e['bank_name']}): {e['parse_error']}")


if __name__ == "__main__":
    main()
