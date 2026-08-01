import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function SimulationPage({ onNext }) {
    const [employment, setEmployment] = useState("FULL_TIME");
    const [salaryTransferable, setSalaryTransferable] = useState(true);
    const [lumpSum, setLumpSum] = useState(20000000);
    const [emergencyFund, setEmergencyFund] = useState(3000000);
    const [monthlySaving, setMonthlySaving] = useState(1000000);
    const [targetMonths, setTargetMonths] = useState(12);
    const [cardSpend6m, setCardSpend6m] = useState([250000, 300000, 200000, 280000, 320000, 220000]);
    const [cardBudgetCap, setCardBudgetCap] = useState(300000);
    const [existingBank, setExistingBank] = useState("KB국민은행");

    const [isLoading, setIsLoading] = useState(false);
    const [resultData, setResultData] = useState(null);
    const [conditionAnswers, setConditionAnswers] = useState({});
    const [selectedProductId, setSelectedProductId] = useState(null);

    const handleCardSpendChange = (index, value) => {
        const updated = [...cardSpend6m];
        updated[index] = Number(value) || 0;
        setCardSpend6m(updated);
    };

    const buildProfilePayload = (answers = conditionAnswers) => ({
        employment: employment,
        salaryTransferable: Boolean(salaryTransferable),
        lumpSum: Math.max(0, Number(lumpSum) || 0),
        emergencyFund: Math.max(0, Number(emergencyFund) || 0),
        monthlySaving: Math.max(0, Number(monthlySaving) || 0),
        targetMonths: Number(targetMonths) || 12,
        cardSpend6m: cardSpend6m.map(v => Math.max(0, Number(v) || 0)),
        cardBudgetCap: Math.max(0, Number(cardBudgetCap) || 0),
        existingBanks: existingBank ? [existingBank] : [],
        conditionAnswers: answers
    });

    const requestSimulation = async (productIds, answers = conditionAnswers) => {
        setIsLoading(true);
        const profilePayload = buildProfilePayload(answers);

        const requestBody = {
            profile: profilePayload,
            productIds
        };

        try {
            const response = await fetch('/api/v1/simulate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody),
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(`시뮬레이션 실패: ${response.status} - ${errText}`);
            }

            const data = await response.json();
            console.log("백엔드 시뮬레이션 응답 성공:", data);
            setResultData(data);
        } catch (error) {
            console.error("API 연동 에러:", error);
            alert(`시뮬레이션 오류:\n${error.message}`);
        } finally {
            setIsLoading(false);
        }
    };

    const handleRunSimulation = () => {
        setSelectedProductId(null);
        requestSimulation(null);
    };

    const handleProductDetail = (productId) => {
        setSelectedProductId(productId);
        requestSimulation([productId]);
    };

    const handleConditionAnswer = (conditionId, answer) => {
        const nextAnswers = { ...conditionAnswers, [conditionId]: answer };
        setConditionAnswers(nextAnswers);
        requestSimulation(selectedProductId ? [selectedProductId] : null, nextAnswers);
    };

    const handleContinue = () => {
        onNext?.(buildProfilePayload());
    };

    // 금액 단위 변환 헬퍼 (예: 20000000 -> 2,000만원)
    const formatKoreanMoney = (amount) => {
        const num = Number(amount) || 0;
        if (num >= 10000) {
            return `${(num / 10000).toLocaleString()}만원`;
        }
        return `${num.toLocaleString()}원`;
    };

    return (
        <div className="space-y-8 animate-fadeIn max-w-6xl mx-auto py-6">
            <div className="text-center space-y-2">
                <span className="bg-amber-200/70 text-amber-900 text-xs font-black px-3 py-1 rounded-full border border-amber-300/50">
                  🐣 Step 1. 자산 프로필 & 몬테카를로 AI 진단
                </span>
                <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                    내 금융 자원으로 최상의 금리를 진단해보세요
                </h1>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
                <div className="lg:col-span-5">
                    <Card title="내 자산 프로필" icon="👤">
                        <div className="space-y-4 text-xs font-bold text-amber-900">
                            {/* 근로 형태 / 급여 이체 */}
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block mb-1.5 text-amber-800">근로 형태</label>
                                    <select
                                        value={employment}
                                        onChange={(e) => setEmployment(e.target.value)}
                                        className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold"
                                    >
                                        <option value="FULL_TIME">정규직</option>
                                        <option value="CONTRACT">계약직</option>
                                        <option value="FREELANCER">프리랜서</option>
                                        <option value="SELF_EMPLOYED">자영업</option>
                                        <option value="STUDENT">학생</option>
                                        <option value="UNEMPLOYED">무직</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="block mb-1.5 text-amber-800">급여이체 가능 여부</label>
                                    <select
                                        value={salaryTransferable ? "Y" : "N"}
                                        onChange={(e) => setSalaryTransferable(e.target.value === "Y")}
                                        className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold"
                                    >
                                        <option value="Y">가능 (Y)</option>
                                        <option value="N">불가능 (N)</option>
                                    </select>
                                </div>
                            </div>

                            {/* 목표 저축 기간 선택 UI */}
                            <div>
                                <label className="block mb-1.5 text-amber-800 font-extrabold text-xs">🎯 목표 저축 기간</label>
                                <select
                                    value={targetMonths}
                                    onChange={(e) => setTargetMonths(Number(e.target.value))}
                                    className="w-full px-3.5 py-3 bg-amber-100/50 border border-amber-300 rounded-2xl text-amber-950 font-black"
                                >
                                    <option value={6}>6개월 (단기 굴리기)</option>
                                    <option value={12}>12개월 (1년 표준)</option>
                                    <option value={24}>24개월 (2년 장기)</option>
                                    <option value={36}>36개월 (3년 목돈)</option>
                                </select>
                            </div>

                            {/* 자산 금액 관련 */}
                            <div className="grid grid-cols-3 gap-2">
                                <div>
                                    <label className="text-amber-800 text-[11px] block mb-1.5">보유 목돈</label>
                                    <input
                                        type="number"
                                        value={lumpSum}
                                        onChange={(e) => setLumpSum(Number(e.target.value))}
                                        className="w-full px-2.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-extrabold text-xs"
                                    />
                                    <p className="text-[10px] text-amber-700/70 font-semibold mt-1 text-right">{formatKoreanMoney(lumpSum)}</p>
                                </div>
                                <div>
                                    <label className="text-amber-800 text-[11px] block mb-1.5">비상금</label>
                                    <input
                                        type="number"
                                        value={emergencyFund}
                                        onChange={(e) => setEmergencyFund(Number(e.target.value))}
                                        className="w-full px-2.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold text-xs"
                                    />
                                    <p className="text-[10px] text-amber-700/70 font-semibold mt-1 text-right">{formatKoreanMoney(emergencyFund)}</p>
                                </div>
                                <div>
                                    <label className="text-amber-800 text-[11px] block mb-1.5">월 저축여력</label>
                                    <input
                                        type="number"
                                        value={monthlySaving}
                                        onChange={(e) => setMonthlySaving(Number(e.target.value))}
                                        className="w-full px-2.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold text-xs"
                                    />
                                    <p className="text-[10px] text-amber-700/70 font-semibold mt-1 text-right">{formatKoreanMoney(monthlySaving)}</p>
                                </div>
                            </div>

                            {/* 기존 주거래 은행 */}
                            <div>
                                <label className="block mb-1.5 text-amber-800">기존 주 거래 은행</label>
                                <select
                                    value={existingBank}
                                    onChange={(e) => setExistingBank(e.target.value)}
                                    className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold"
                                >
                                    <option value="KB국민은행">KB국민은행</option>
                                    <option value="신한은행">신한은행</option>
                                    <option value="우리은행">우리은행</option>
                                    <option value="하나은행">하나은행</option>
                                    <option value="카카오뱅크">카카오뱅크</option>
                                    <option value="토스뱅크">토스뱅크</option>
                                    <option value="IBK기업은행">IBK기업은행</option>
                                    <option value="NH농협은행">NH농협은행</option>
                                </select>
                            </div>

                            {/* 카드 소비 데이터 */}
                            <div>
                                <label className="block mb-1.5 text-amber-800">최근 6개월 카드 사용액 (원)</label>
                                <div className="grid grid-cols-3 gap-2">
                                    {cardSpend6m.map((spend, idx) => (
                                        <div key={idx} className="flex items-center space-x-1">
                                            <span className="text-[10px] text-amber-700 font-bold">{idx + 1}M:</span>
                                            <input
                                                type="number"
                                                value={spend}
                                                onChange={(e) => handleCardSpendChange(idx, e.target.value)}
                                                className="w-full px-2 py-1.5 bg-amber-50/60 border border-amber-200/80 rounded-xl text-amber-950 text-xs font-bold"
                                            />
                                        </div>
                                    ))}
                                </div>
                            </div>

                            <div>
                                <div className="flex justify-between items-center mb-1.5">
                                    <label className="text-amber-800">월 카드 예산 상한 (원)</label>
                                    <span className="text-[10px] text-amber-700 font-bold">{formatKoreanMoney(cardBudgetCap)}</span>
                                </div>
                                <input
                                    type="number"
                                    value={cardBudgetCap}
                                    onChange={(e) => setCardBudgetCap(Number(e.target.value))}
                                    className="w-full px-4 py-3 bg-amber-100/40 border border-amber-300 rounded-2xl text-amber-950 font-black text-sm"
                                />
                            </div>

                            <Button
                                onClick={handleRunSimulation}
                                disabled={isLoading}
                                className="w-full py-4 text-base bg-amber-500 text-white font-black rounded-2xl hover:bg-amber-600 transition-colors shadow-md mt-4"
                            >
                                {isLoading ? "상품 AI 진단 분석 중... 🎲" : "⚡ 전체 상품 AI 시뮬레이션 실행"}
                            </Button>
                        </div>
                    </Card>
                </div>

                {/* 우측 시뮬레이션 결과 리스트 */}
                <div className="lg:col-span-7 space-y-6">
                    {resultData?.cardBudget && (
                        <div className="bg-amber-100/80 p-5 rounded-2xl border border-amber-300 flex justify-between items-center text-amber-950 text-xs font-bold shadow-sm">
                            <div>
                                📊 최근 6개월 카드 소비 분석:
                                <span className="text-amber-800 ml-1">
                                    평균 {resultData.cardBudget.mean?.toLocaleString()}원 (표준편차: {resultData.cardBudget.std?.toLocaleString()}원)
                                </span>
                            </div>
                            <span className="bg-amber-500 text-white px-2.5 py-1 rounded-lg text-[10px] font-black">
                                {resultData.cardBudget.distribution || "NORMAL"}
                            </span>
                        </div>
                    )}

                    {resultData?.products && resultData.products.length > 0 ? (
                        <div className="space-y-4 max-h-[600px] overflow-y-auto pr-2">
                            <div className="flex justify-between items-center bg-white p-3 rounded-2xl border border-amber-100">
                                <p className="text-xs font-black text-amber-900">
                                    {selectedProductId
                                        ? `🔍 선택 상품 우대조건 정밀 분석 중`
                                        : `🎯 총 ${resultData.products.length}개 상품 진단 완료! (상위 10개 표시)`}
                                </p>
                                {selectedProductId && (
                                    <button
                                        type="button"
                                        onClick={handleRunSimulation}
                                        className="text-xs font-extrabold text-amber-800 hover:text-amber-950 bg-amber-200/80 px-3 py-1.5 rounded-xl border border-amber-300 transition-all shadow-2xs"
                                    >
                                        👈 전체 상품 목록으로
                                    </button>
                                )}
                            </div>

                            {/* 상위 10개 상품만 표시 */}
                            {resultData.products.slice(0, 10).map((prod, idx) => (
                                <Card key={prod.productId || idx} title={prod.productName} subtitle={`${prod.bankName || '은행'} | ${prod.termMonths || 12}개월`}>
                                    <div className="space-y-3">
                                        <div className="flex justify-between items-center p-3 bg-amber-50/80 rounded-xl border border-amber-200">
                                            <div>
                                                <p className="text-[10px] text-amber-700 font-bold">기본금리 → 최고금리</p>
                                                <p className="text-xs font-black text-amber-900">{prod.baseRate}% ~ {prod.advertisedMaxRate}%</p>
                                            </div>
                                            <div className="text-right">
                                                <p className="text-[10px] text-amber-700 font-bold">굴리미 AI 기대금리 E[r]</p>
                                                <p className="text-lg font-black text-amber-600">연 {prod.expectedRate}% 🎉</p>
                                            </div>
                                        </div>

                                        {prod.confirmationQuestions?.length > 0 && (
                                            <div className="space-y-3 rounded-2xl border border-blue-200 bg-blue-50/70 p-4">
                                                <div>
                                                    <p className="text-xs font-black text-blue-950">
                                                        이 항목을 확인하면 금리를 더 정확히 계산할 수 있어요
                                                    </p>
                                                    <p className="mt-1 text-[11px] font-semibold text-blue-700">
                                                        한 번에 최대 3개만 여쭤봅니다.
                                                    </p>
                                                </div>
                                                {prod.confirmationQuestions.map((question) => (
                                                    <div
                                                        key={question.conditionId}
                                                        className="rounded-xl border border-blue-100 bg-white p-3"
                                                    >
                                                        <p className="text-xs font-bold text-blue-950">
                                                            {question.question}
                                                        </p>
                                                        <p className="mt-1 text-[10px] font-semibold text-blue-600">
                                                            금리 영향 +{question.rateImpact}%p
                                                        </p>
                                                        <div className="mt-2 flex gap-2">
                                                            <button
                                                                type="button"
                                                                onClick={() => handleConditionAnswer(question.conditionId, true)}
                                                                disabled={isLoading}
                                                                className="flex-1 rounded-lg bg-blue-600 px-3 py-2 text-[11px] font-black text-white disabled:opacity-50 hover:bg-blue-700 transition-colors"
                                                            >
                                                                가능해요
                                                            </button>
                                                            <button
                                                                type="button"
                                                                onClick={() => handleConditionAnswer(question.conditionId, false)}
                                                                disabled={isLoading}
                                                                className="flex-1 rounded-lg border border-blue-200 bg-white px-3 py-2 text-[11px] font-black text-blue-800 disabled:opacity-50 hover:bg-blue-50 transition-colors"
                                                            >
                                                                어려워요
                                                            </button>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        )}

                                        {!selectedProductId && (
                                            <button
                                                type="button"
                                                onClick={() => handleProductDetail(prod.productId)}
                                                disabled={isLoading}
                                                className="w-full rounded-xl border border-amber-300 bg-white px-4 py-2.5 text-xs font-black text-amber-900 hover:bg-amber-100 transition-colors disabled:opacity-50 shadow-sm"
                                            >
                                                이 상품 금리 정확히 계산하기 🔍
                                            </button>
                                        )}
                                    </div>
                                </Card>
                            ))}
                        </div>
                    ) : (
                        <Card title="AI 시뮬레이션 대기 중" icon="🎲">
                            <div className="text-center py-12 text-amber-800/60 font-bold text-xs space-y-2">
                                <p className="text-2xl">👈</p>
                                <p>왼쪽 자산 프로필을 확인하고 [⚡ 전체 상품 AI 시뮬레이션 실행] 버튼을 눌러보세요!</p>
                            </div>
                        </Card>
                    )}
                </div>
            </div>

            {resultData?.products?.length > 0 && (
                <div className="flex justify-end pt-4">
                    <Button onClick={handleContinue} disabled={isLoading} className="text-sm px-8 py-4 bg-amber-500 text-white font-black rounded-2xl hover:bg-amber-600 transition-all shadow-lg">
                        추천 플랜 확인하기 (Step 2) →
                    </Button>
                </div>
            )}
        </div>
    );
}