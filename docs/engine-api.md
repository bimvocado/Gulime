# 저축 엔진 입출력 인터페이스

## 계산 요청

`POST /api/v1/simulate`

```json
{
  "profile": {
    "employment": "FREELANCER",
    "salaryTransferable": false,
    "lumpSum": 5000000,
    "emergencyFund": 1000000,
    "monthlySaving": 500000,
    "targetMonths": 12,
    "cardSpend6m": [220000, 310000, 180000, 270000, 350000, 170000],
    "cardBudgetCap": 300000,
    "existingBanks": ["국민은행"],
    "conditionAnswers": {}
  },
  "productIds": ["0010927_010200100070_12M"]
}
```

`conditionAnswers`는 선택 입력이다. 키는 응답의 `conditionId`, 값은 사용자가
해당 조건을 달성할 수 있는지 여부다. 기존 클라이언트처럼 필드를 생략해도 된다.

## 추가 확인 질문

한 상품을 상세 계산했을 때만 금리 영향도가 큰 질문을 최대 3개 반환한다. 전체 상품
계산에서는 질문을 반환하지 않는다.

```json
{
  "confirmationQuestions": [
    {
      "conditionId": "569",
      "question": "아파트관리비 이체 조건을 충족할 수 있나요?",
      "rateImpact": 0.1
    }
  ]
}
```

미응답 조건은 자동 성공으로 처리하지 않는다. 달성 확률 50%, 신뢰구간 0~100%,
상태 `NEEDS_CONFIRMATION`으로 계산한다.

## 답변 후 재계산

```json
{
  "profile": {
    "employment": "FREELANCER",
    "salaryTransferable": false,
    "lumpSum": 5000000,
    "emergencyFund": 1000000,
    "monthlySaving": 500000,
    "targetMonths": 12,
    "cardSpend6m": [220000, 310000, 180000, 270000, 350000, 170000],
    "cardBudgetCap": 300000,
    "existingBanks": ["국민은행"],
    "conditionAnswers": {
      "567": true,
      "568": true,
      "569": false,
      "570": true,
      "571": true,
      "573": false,
      "574": true
    }
  },
  "productIds": ["0010927_010200100070_12M"]
}
```

KB내맘대로적금 12개월 회귀 테스트의 기대 결과는 기본금리 2.55%, 광고 최고금리
3.15%, 사용자 달성 가능 최고금리 3.05%다.
