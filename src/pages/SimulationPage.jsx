import React, { useState, useEffect } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

const SIMULATION_STORAGE_KEY = 'gulimi_simulation_state';

// 💡 가이드용 placeholder 예시값 (실제 state로 들어가진 않음)
const PLACEHOLDERS = {
    lumpSum: "10000000",
    emergencyFund: "3000000",
    monthlySaving: "500000",
    cardSpend: "500000",
    cardBudgetCap: "1000000"
};

const getInitialSimulationState = () => {
    try {
        const saved = localStorage.getItem(SIMULATION_STORAGE_KEY);
        if (saved) return JSON.parse(saved);
    } catch (e) {
        console.error("Simulation storage parse error:", e);
    }
    return null;
};

export default function SimulationPage({ initialProfile, onNext }) {
    const savedState = getInitialSimulationState();

    // 💡 입력폼 상태 (비어있는 경우 빈 문자열 "" 유지)
    const [employment, setEmployment] = useState(
        savedState?.employment || initialProfile?.employment || "FULL_TIME"
    );
    const [salaryTransferable, setSalaryTransferable] = useState(
        savedState?.salaryTransferable ?? initialProfile?.salaryTransferable ?? true
    );
    const [lumpSum, setLumpSum] = useState(
        savedState?.lumpSum ?? initialProfile?.lumpSum ?? ""
    );
    const [emergencyFund, setEmergencyFund] = useState(
        savedState?.emergencyFund ?? initialProfile?.emergencyFund ?? ""
    );
    const [monthlySaving, setMonthlySaving] = useState(
        savedState?.monthlySaving ?? initialProfile?.monthlySaving ?? ""
    );
    const [targetMonths, setTargetMonths] = useState(
        savedState?.targetMonths ?? initialProfile?.targetMonths ?? 12
    );
    const [cardSpend6m, setCardSpend6m] = useState(
        savedState?.cardSpend6m || initialProfile?.cardSpend6m || ["", "", "", "", "", ""]
    );
    const [cardBudgetCap, setCardBudgetCap] = useState(
        savedState?.cardBudgetCap ?? initialProfile?.cardBudgetCap ?? ""
    );
    const [existingBank, setExistingBank] = useState(
        savedState?.existingBank || (initialProfile?.existingBanks?.[0]) || "KB국민은행"
    );

    // 💡 유효성 검사 에러 상태 관리 (어떤 필드가 비었는지 체크)
    const [errors, setErrors] = useState({});
    const [isLoading, setIsLoading] = useState(false);
    const [resultData, setResultData] = useState(savedState?.resultData || null);
    const [conditionAnswers, setConditionAnswers] = useState(savedState?.conditionAnswers || {});
    const [selectedProductId, setSelectedProductId] = useState(savedState?.selectedProductId || null);

    useEffect(() => {
        const currentState = {
            employment,
            salaryTransferable,
            lumpSum,
            emergencyFund,
            monthlySaving,
            targetMonths,
            cardSpend6m,
            cardBudgetCap,
            existingBank,
            resultData,
            conditionAnswers,
            selectedProductId
        };
        localStorage.setItem(SIMULATION_STORAGE_KEY, JSON.stringify(currentState));
    }, [
        employment, salaryTransferable, lumpSum, emergencyFund,
        monthlySaving, targetMonths, cardSpend6m, cardBudgetCap,
        existingBank, resultData, conditionAnswers, selectedProductId
    ]);

    const handleCardSpendChange = (index, value) => {
        const updated = [...cardSpend6m];
        updated[index] = value;
        setCardSpend6m(updated);
        // 입력 시 해당 영역 에러 해제
        if (errors.cardSpend6m) {
            setErrors(prev => ({ ...prev, cardSpend6m: false }));
        }
    };

    // 💡 미입력 항목 검증 로직
    const validateInputs = () => {
        const newErrors = {};

        if (lumpSum === "" || lumpSum === null || isNaN(lumpSum)) newErrors.lumpSum = "보유 목돈을 입력해 주세요.";
        if (emergencyFund === "" || emergencyFund === null || isNaN(emergencyFund)) newErrors.emergencyFund = "비상금을 입력해 주세요.";
        if (monthlySaving === "" || monthlySaving === null || isNaN(monthlySaving)) newErrors.monthlySaving = "월 저축여력을 입력해 주세요.";
        if (cardBudgetCap === "" || cardBudgetCap === null || isNaN(cardBudgetCap)) newErrors.cardBudgetCap = "월 카드 예산 상한을 입력해 주세요.";

        // 카드 6개월 사용액 중 하나라도 비어있는지 확인
        const hasEmptyCardSpend = cardSpend6m.some(v => v === "" || v === null || isNaN(v));
        if (hasEmptyCardSpend) newErrors.cardSpend6m = "6개월 카드 사용액을 모두 입력해 주세요.";

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const buildProfilePayload = (answers = conditionAnswers) => ({
        employment,
        salaryTransferable: Boolean(salaryTransferable),
        lumpSum: Number(lumpSum),
        emergencyFund: Number(emergencyFund),
        monthlySaving: Number(monthlySaving),
        targetMonths: Number(targetMonths),
        cardSpend6m: cardSpend6m.map(v => Number(v)),
        cardBudgetCap: Number(cardBudgetCap),
        existingBanks: [existingBank],
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
                headers: {
                    'Content-Type': 'application/json',
                    'Cache-Control': 'no-cache'
                },
                body: JSON.stringify(requestBody),
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(`시뮬레이션 실패: ${response.status} - ${errText}`);
            }

            const data = await response.json();
            setResultData(data);
        } catch (error) {
            console.error("API 연동 에러:", error);
            alert(`시뮬레이션 오류:\n${error.message}`);
        } finally {
            setIsLoading(false);
        }
    };

    const handleRunSimulation = () => {
        // 검증 실패 시 실행 차단
        if (!validateInputs()) {
            return;
        }
        setSelectedProductId(null);
        setConditionAnswers({});
        requestSimulation(null, {});
    };

    const handleProductDetail = (productId) => {
        if (!validateInputs()) return;
        setSelectedProductId(productId);
        requestSimulation([productId], conditionAnswers);
    };

    const handleConditionAnswer = (conditionId, answer) => {
        const nextAnswers = { ...conditionAnswers, [conditionId]: answer };
        setConditionAnswers(nextAnswers);
        requestSimulation(selectedProductId ? [selectedProductId] : null, nextAnswers);
    };

    const handleContinue = () => {
        if (!validateInputs()) return;
        localStorage.removeItem('gulimi_options_state');
        localStorage.removeItem('gulimi_selected_option');
        onNext?.(buildProfilePayload());
    };

    const formatKoreanMoney = (amount) => {
        if (amount === "" || amount === null || isNaN(amount)) return "";
        const num = Number(amount) || 0;
        if (num === 0) return "0원";
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
                            {Object.keys(errors).length > 0 && (
                                <div className="p-3 bg-red-100 border border-red-300 text-red-700 rounded-xl text-xs font-extrabold animate-pulse">
                                    ⚠️ 입력되지 않은 항목이 있습니다. 연한 안냇값을 참고하여 적어주세요!
                                </div>
                            )}

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

                            <div className="grid grid-cols-3 gap-2">
                                <div>
                                    <label className="text-amber-800 text-[11px] block mb-1.5">보유 목돈</label>
                                    <input
                                        type="number"
                                        value={lumpSum}
                                        placeholder={PLACEHOLDERS.lumpSum}
                                        onChange={(e) => {
                                            setLumpSum(e.target.value);
                                            setErrors(prev => ({ ...prev, lumpSum: false }));
                                        }}
                                        className={`w-full px-2.5 py-3 bg-amber-50/60 border ${errors.lumpSum ? 'border-red-500 ring-2 ring-red-200' : 'border-amber-200/80'} rounded-2xl text-amber-950 placeholder:text-amber-900/30 placeholder:font-normal font-extrabold text-xs transition-all`}
                                    />
                                    <p className="text-[10px] text-amber-700/70 font-semibold mt-1 text-right min-h-[14px]">
                                        {formatKoreanMoney(lumpSum)}
                                    </p>
                                </div>
                                <div>
                                    <label className="text-amber-800 text-[11px] block mb-1.5">비상금</label>
                                    <input
                                        type="number"
                                        value={emergencyFund}
                                        placeholder={PLACEHOLDERS.emergencyFund}
                                        onChange={(e) => {
                                            setEmergencyFund(e.target.value);
                                            setErrors(prev => ({ ...prev, emergencyFund: false }));
                                        }}
                                        className={`w-full px-2.5 py-3 bg-amber-50/60 border ${errors.emergencyFund ? 'border-red-500 ring-2 ring-red-200' : 'border-amber-200/80'} rounded-2xl text-amber-950 placeholder:text-amber-900/30 placeholder:font-normal font-bold text-xs transition-all`}
                                    />
                                    <p className="text-[10px] text-amber-700/70 font-semibold mt-1 text-right min-h-[14px]">
                                        {formatKoreanMoney(emergencyFund)}
                                    </p>
                                </div>
                                <div>
                                    <label className="text-amber-800 text-[11px] block mb-1.5">월 저축여력</label>
                                    <input
                                        type="number"
                                        value={monthlySaving}
                                        placeholder={PLACEHOLDERS.monthlySaving}
                                        onChange={(e) => {
                                            setMonthlySaving(e.target.value);
                                            setErrors(prev => ({ ...prev, monthlySaving: false }));
                                        }}
                                        className={`w-full px-2.5 py-3 bg-amber-50/60 border ${errors.monthlySaving ? 'border-red-500 ring-2 ring-red-200' : 'border-amber-200/80'} rounded-2xl text-amber-950 placeholder:text-amber-900/30 placeholder:font-normal font-bold text-xs transition-all`}
                                    />
                                    <p className="text-[10px] text-amber-700/70 font-semibold mt-1 text-right min-h-[14px]">
                                        {formatKoreanMoney(monthlySaving)}
                                    </p>
                                </div>
                            </div>

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
                                    <option value="부산은행">부산은행</option>
                                </select>
                            </div>

                            <div>
                                <label className="block mb-1.5 text-amber-800">최근 6개월 카드 사용액 (원)</label>
                                <div className="grid grid-cols-3 gap-2">
                                    {cardSpend6m.map((spend, idx) => (
                                        <div key={idx} className="flex items-center space-x-1">
                                            <span className="text-[10px] text-amber-700 font-bold">{idx + 1}M:</span>
                                            <input
                                                type="number"
                                                value={spend}
                                                placeholder={PLACEHOLDERS.cardSpend}
                                                onChange={(e) => handleCardSpendChange(idx, e.target.value)}
                                                className={`w-full px-2 py-1.5 bg-amber-50/60 border ${errors.cardSpend6m ? 'border-red-500 ring-1 ring-red-200' : 'border-amber-200/80'} rounded-xl text-amber-950 text-xs font-bold placeholder:text-amber-900/30 placeholder:font-normal transition-all`}
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
                                    placeholder={PLACEHOLDERS.cardBudgetCap}
                                    onChange={(e) => {
                                        setCardBudgetCap(e.target.value);
                                        setErrors(prev => ({ ...prev, cardBudgetCap: false }));
                                    }}
                                    className={`w-full px-4 py-3 bg-amber-100/40 border ${errors.cardBudgetCap ? 'border-red-500 ring-2 ring-red-200' : 'border-amber-300'} rounded-2xl text-amber-950 font-black text-sm placeholder:text-amber-900/30 placeholder:font-normal transition-all`}
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

                            {resultData.products.slice(0, 10).map((prod, idx) => (
                                <Card key={prod.productId || idx} title={prod.productName} subtitle={`${prod.bankName || '은행'} | ${prod.termMonths || 12}개월`}>
                                    <div className="space-y-3">
                                        <div className="flex justify-between items-center p-3 bg-amber-50/80 rounded-xl border border-amber-200">
                                            <div>
                                                <p className="text-[10px] text-amber-700 font-bold">기본금리 → 최고금리</p>
                                                <p className="text-xs font-black text-amber-900">
                                                    {prod.baseRate}% ~ {prod.maxRate ?? prod.advertisedMaxRate}%
                                                </p>
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
                                                </div>
                                                {prod.confirmationQuestions.map((question) => (
                                                    <div key={question.conditionId} className="rounded-xl border border-blue-100 bg-white p-3">
                                                        <p className="text-xs font-bold text-blue-950">{question.question}</p>
                                                        <p className="mt-1 text-[10px] font-semibold text-blue-600">금리 영향 +{question.rateImpact}%p</p>
                                                        <div className="mt-2 flex gap-2">
                                                            <button
                                                                type="button"
                                                                onClick={() => handleConditionAnswer(question.conditionId, true)}
                                                                disabled={isLoading}
                                                                className="flex-1 rounded-lg bg-blue-600 px-3 py-2 text-[11px] font-black text-white"
                                                            >
                                                                가능해요
                                                            </button>
                                                            <button
                                                                type="button"
                                                                onClick={() => handleConditionAnswer(question.conditionId, false)}
                                                                disabled={isLoading}
                                                                className="flex-1 rounded-lg border border-blue-200 bg-white px-3 py-2 text-[11px] font-black text-blue-800"
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
                                                className="w-full rounded-xl border border-amber-300 bg-white px-4 py-2.5 text-xs font-black text-amber-900 hover:bg-amber-100"
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
                                <p>왼쪽 자산 프로필을 입력하고 [⚡ 전체 상품 AI 시뮬레이션 실행] 버튼을 눌러보세요!</p>
                            </div>
                        </Card>
                    )}
                </div>
            </div>

            {resultData?.products?.length > 0 && (
                <div className="flex justify-end pt-4">
                    <Button onClick={handleContinue} disabled={isLoading} className="text-sm px-8 py-4 bg-amber-500 text-white font-black rounded-2xl hover:bg-amber-600 shadow-lg">
                        추천 플랜 확인하기 (Step 2) →
                    </Button>
                </div>
            )}
        </div>
    );
}