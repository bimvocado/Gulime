"""Split bundled validation conditions using validation source_text only."""
from __future__ import annotations

import copy
import json
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "data" / "validation_set.json"


def atom(old, name, kind, rate, threshold=None, *, tiers=None):
    item = copy.deepcopy(old)
    item["condition_name"] = name
    item["type"] = kind
    item["resource"] = "NONE"
    item["tiers"] = tiers or [{"threshold": threshold, "rate_bonus": rate}]
    item["period_months"] = None
    item["required_months"] = None
    item["payout"] = "ALL_OR_NOTHING"
    item["branch"] = None
    item["exclusive_group"] = None
    item["selectable"] = None
    return item


def specs(old, rows):
    return [atom(old, *row) for row in rows]


def split(product, old):
    tables = {
        "iM함께예금": [
            ("전월 총수신 평잔 30만원 이상 또는 첫만남플러스통장 보유", "OTHER", 0.1, None),
            ("주택청약상품 보유", "PRODUCT_HOLDING", 0.1, None),
            ("iM함께적금 동시 가입 및 만기 보유", "PRODUCT_HOLDING", 0.1, None),
            ("오픈뱅킹에 다른 은행 계좌 등록", "CHANNEL", 0.1, None),
            ("인터넷·모바일뱅킹 가입", "CHANNEL", 0.05, None),
        ],
        "iM스마트예금": [
            ("주택청약종합저축 보유 또는 카드 결제실적 보유", "OTHER", 0.2, None),
            ("인터넷·모바일뱅킹 가입", "CHANNEL", 0.05, None),
        ],
        "미즈월복리정기예금": [
            ("요구불예금 평잔", "AVG_BALANCE", None, None),
            ("신용·체크카드 결제실적", "CARD_SPEND", None, None),
        ],
        "굿스타트예금": [
            ("최근 1년 정기예금 첫거래", "FIRST_TRADE", 0.4, 12),
            ("개인신용정보 수집·이용 동의 유지", "MARKETING_AGREE", 0.1, None),
        ],
        "J정기예금\n(만기지급식)": [
            ("비대면 채널 가입", "CHANNEL", 0.3, None),
            ("계약기간 절반 이상 매월 Jbank 로그인", "CHANNEL", 0.2, None),
        ],
        "스마일드림 \n정기예금\n(개인/선이자\n지급식)": [
            ("김만덕나눔적금 보유 또는 만기 해지", "PRODUCT_HOLDING", 0.2, None),
            ("탐나는전 체크카드 보유", "PRODUCT_HOLDING", 0.1, None),
        ],
        "JB 123 정기예금\n (만기일시지급식)": [
            ("자동재예치 회차 우대", "LOYALTY", None, None),
            ("최근 6개월 정기예금 미보유", "FIRST_TRADE", 0.5, 6),
            ("개인신용정보 수집·이용 및 상품서비스 안내수단 전체 동의", "MARKETING_AGREE", 0.1, None),
        ],
        "BNK더조은정기예금": [
            ("가입·재예치 금액 2천만원 이상", "MIN_DEPOSIT", 0.1, 20_000_000),
            ("금리우대쿠폰 등록", "OTHER", 0.2, None),
            ("경남은행 오픈뱅킹 가입 및 유지", "CHANNEL", 0.1, None),
            ("자동재예치 가입", "LOYALTY", 0.05, None),
            ("마케팅 및 모바일메시지 수신 동의", "MARKETING_AGREE", 0.1, None),
        ],
        "The든든예금(시즌2)": [
            ("마케팅 및 모바일메시지 수신 동의", "MARKETING_AGREE", 0.05, None),
            ("최근 12개월 신규·해지 이력 없는 신규고객", "FIRST_TRADE", 0.1, 12),
            ("비대면 이벤트 금리(기간별 최대)", "UNCONDITIONAL", 1.4, None),
        ],
        "The파트너예금": [
            ("거래기간 5년 이상 및 마케팅 동의", "OTHER", 0.2, None),
            ("급여·연금·가맹점대금 입금", "OTHER", 0.1, None),
            ("경남은행 카드 결제실적 보유", "CARD_SPEND", 0.1, None),
        ],
        "IBK평생한가족통장(실세금리정기예금)": [
            ("최초 신규 고객", "FIRST_TRADE", 0.05, None),
            ("재예치 고객", "LOYALTY", 0.05, None),
            ("장기거래 고객", "LOYALTY", 0.05, None),
            ("주거래 우대", "OTHER", 0.15, None),
        ],
        "신한My플러스 정기예금": [
            ("정기예금 미보유", "FIRST_TRADE", 0.1, None),
            ("소득 이체", "SALARY_TRANSFER", 0.1, None),
        ],
        "NH왈츠회전예금 II": [
            ("급여이체 50만원 이상", "SALARY_TRANSFER", 0.1, 500_000),
            ("4회전부터 트리플 회전 우대", "LOYALTY", 0.1, 4),
        ],
        "NH내가Green초록세상예금": [
            ("온실가스 줄이기 실천서약 동의", "OTHER", 0.1, None),
            ("통장 미발급", "OTHER", 0.1, None),
            ("손하나로인증 서비스 등록", "CHANNEL", 0.1, None),
            ("NH내가Green초록세상적금 동시 보유", "PRODUCT_HOLDING", 0.1, None),
        ],
        "NH고향사랑기부예금": [
            ("고향사랑기부금 납부", "OTHER", 0.3, None),
            ("만 65세 이상 또는 만 19~34세 고객", "OTHER", 0.1, None),
            ("가입고객 모두 적용 특별금리", "UNCONDITIONAL", 0.05, None),
        ],
        "Sh해양플라스틱Zero!예금\n(만기일시지급식)": [
            ("해양플라스틱 감축 서약", "OTHER", 0.1, None),
            ("봉사활동 또는 상품 홍보", "OTHER", 0.15, None),
            ("입출금통장 최초 신규", "FIRST_TRADE", 0.1, None),
            ("자동이체 출금실적", "AUTO_TRANSFER", 0.1, None),
        ],
        "우리SUPER주거래적금": [
            ("급여·연금 이체", "SALARY_TRANSFER", 0.7, None),
            ("공과금 자동이체 출금", "AUTO_TRANSFER", 0.3, None),
            ("우리카드 결제 10만원 이상", "CARD_SPEND", 0.3, 100_000),
            ("전화·SMS 마케팅 동의 유지", "MARKETING_AGREE", 0.1, None),
        ],
        "WON적금": [
            ("우리꿈통장·WON통장에 연결 가입", "PRODUCT_HOLDING", 0.1, None),
            ("오픈뱅킹에 타행계좌 등록", "CHANNEL", 0.1, None),
        ],
        "iM함께적금": [
            ("전월 총수신 평잔 30만원 이상 또는 첫만남플러스통장 보유", "OTHER", 0.1, None),
            ("주택청약상품 보유", "PRODUCT_HOLDING", 0.2, None),
            ("iM함께예금 동시 가입 및 만기 보유", "PRODUCT_HOLDING", 0.2, None),
            ("오픈뱅킹에 다른 은행 계좌 등록", "CHANNEL", 0.3, None),
            ("인터넷·모바일뱅킹 가입", "CHANNEL", 0.05, None),
        ],
        "해피라이프_여행스케치적금V": [
            ("여행스케치외화적금V 동일자 가입", "PRODUCT_HOLDING", 0.5, None),
            ("해지원금 500만원 이상", "MIN_DEPOSIT", 0.2, 5_000_000),
            ("신용·체크카드 사용실적 300만원 이상", "CARD_SPEND", 0.3, 3_000_000),
            ("개인신용정보 동의", "MARKETING_AGREE", 0.2, None),
        ],
        "여행스케치_남도투어적금": [
            ("전라남도 관광지 방문 인증", "OTHER", 1.5, None),
            ("신용·체크카드 사용실적 300만원 이상", "CARD_SPEND", 0.3, 3_000_000),
            ("개인신용정보 동의", "MARKETING_AGREE", 0.1, None),
        ],
        "VIP플러스적금": [
            ("VIP 고객 선정", "OTHER", 0.3, None),
            ("정기예금 500만원 이상 가입 및 유지", "PRODUCT_HOLDING", 0.2, 5_000_000),
        ],
        "jbank 저금통적금": [
            ("자투리 출금계좌 평잔 50만원 이상", "AVG_BALANCE", 0.8, 500_000),
            ("첫거래 또는 상품 1개월 내 재가입", "FIRST_TRADE", 0.5, 1),
            ("목표금액 30만원 이상 설정 및 달성", "MIN_DEPOSIT", 0.5, 300_000),
            ("추천인 우대", "OTHER", 0.3, None),
        ],
        "MZ 플랜적금": [
            ("매월 1회 이상 지속 납입", "OTHER", 0.5, 1),
            ("목표금액 달성", "OTHER", 0.5, None),
            ("카드 합산 사용액 월 10만원 이상", "CARD_SPEND", 0.5, 100_000),
            ("청년 응원 이벤트", "UNCONDITIONAL", 0.5, None),
        ],
        "사이버우대매일부금": [
            ("비대면 채널 신규", "CHANNEL", 0.1, None),
            ("탐나는 J 직장인·주거래통장 가입 및 기본요건 충족", "PRODUCT_HOLDING", 0.1, None),
            ("달리자 파킹통장 가입", "PRODUCT_HOLDING", 0.3, None),
        ],
    }
    if product not in tables:
        return None
    result = specs(old, tables[product])
    if product == "미즈월복리정기예금":
        result[0]["tiers"] = [
            {"threshold": 3_000_000, "rate_bonus": 0.1},
            {"threshold": 5_000_000, "rate_bonus": 0.2},
        ]
        result[1]["tiers"] = [
            {"threshold": 3_000_000, "rate_bonus": 0.05},
            {"threshold": 5_000_000, "rate_bonus": 0.1},
        ]
    if product == "JB 123 정기예금\n (만기일시지급식)":
        result[0]["tiers"] = [
            {"threshold": 1, "rate_bonus": 0.1},
            {"threshold": 2, "rate_bonus": 0.2},
            {"threshold": 3, "rate_bonus": 0.3},
        ]
    if product == "IBK평생한가족통장(실세금리정기예금)":
        for item in result[:3]:
            item["exclusive_group"] = "고객별 우대(최대 0.05%p)"
    return result


def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    changed = []
    for product in data:
        if len(product.get("conditions", [])) != 1:
            continue
        old = product["conditions"][0]
        new = split(product["product_name"], old)
        if new is not None:
            product["conditions"] = new
            changed.append((product["product_name"], old, new))
    for product in data:
        if product["product_name"] == "BNK더조은정기예금":
            for item in product["conditions"]:
                if item["condition_name"] in {"금리우대쿠폰 등록", "자동재예치 가입"}:
                    item["exclusive_group"] = "금리우대쿠폰·자동재예치 중복불가"
        if product["product_name"] == "사이버우대매일부금":
            for item in product["conditions"]:
                if item["type"] == "PRODUCT_HOLDING":
                    item["exclusive_group"] = "통장 보유 우대 중복불가"
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name, old, new in changed:
        print(f"{name}: 1 -> {len(new)}")
        print(f"  BEFORE: {old['condition_name']} / {old['type']} / {old['tiers']}")
        for item in new:
            print(f"  AFTER : {item['condition_name']} / {item['type']} / {item['tiers']}")
    print(f"changed_products={len(changed)}")


if __name__ == "__main__":
    main()
