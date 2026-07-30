import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function SimulationPage({ onNext }) {
    const [employment, setEmployment] = useState("FULL_TIME");
    const [salaryTransferable, setSalaryTransferable] = useState(true);
    const [lumpSum, setLumpSum] = useState(20000000);
    const [emergencyFund, setEmergencyFund] = useState(3000000);
    const [monthlySaving, setMonthlySaving] = useState(1000000);
    const [targetMonths, setTargetMonths] = useState(12); // 👈 [추가] 목표 저축 기간 (기본 12개월)
    const [cardSpend6m, setCardSpend6m] = useState([250000, 300000, 200000, 280000, 320000, 220000]);
    const [cardBudgetCap, setCardBudgetCap] = useState(300000);
    const [existingBank, setExistingBank] = useState("KB");

    const [isLoading, setIsLoading] = useState(false);
    const [resultData, setResultData] = useState(null);

    const handleCardSpendChange = (index, value) => {
        const updated = [...cardSpend6m];
        updated[index] = Number(value) || 0;
        setCardSpend6m(updated);
    };

    const handleRunSimulation = async () => {
        setIsLoading(true);

        // ProfileRequest 규격 (targetMonths 추가)
        const profilePayload = {
            employment: employment,
            salaryTransferable: Boolean(salaryTransferable),
            lumpSum: Math.max(0, Number(lumpSum)),
            emergencyFund: Math.max(0, Number(emergencyFund)),
            monthlySaving: Math.max(0, Number(monthlySaving)),
            targetMonths: Number(targetMonths) || 12, // 👈 [추가] 백엔드로 목표 기간 전달
            cardSpend6m: cardSpend6m.map(v => Math.max(0, Number(v))),
            cardBudgetCap: Math.max(0, Number(cardBudgetCap)),
            existingBanks: [existingBank]
        };

        const requestBody = {
            profile: profilePayload,
            productIds: null
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

            if (onNext) {
                onNext(profilePayload);
            }

        } catch (error) {
            console.error("API 연동 에러:", error);
            alert(`시뮬레이션 오류:\n${error.message}`);
        } finally {
            setIsLoading(false);
        }
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

                            {/* 🎯 [추가] 목표 저축 기간 선택 UI */}
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

                            {/* 자산 금액 관련 (목돈, 비상금, 월 저축) */}
                            <div className="grid grid-cols-3 gap-2">
                                <div>
                                    <label className="block mb-1.5 text-amber-800 text-[11px]">보유 목돈</label>
                                    <input
                                        type="number"
                                        value={lumpSum}
                                        onChange={(e) => setLumpSum(e.target.value)}
                                        className="w-full px-2.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-extrabold text-xs"
                                    />
                                </div>
                                <div>
                                    <label className="block mb-1.5 text-amber-800 text-[11px]">비상금</label>
                                    <input
                                        type="number"
                                        value={emergencyFund}
                                        onChange={(e) => setEmergencyFund(e.target.value)}
                                        className="w-full px-2.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold text-xs"
                                    />
                                </div>
                                <div>
                                    <label className="block mb-1.5 text-amber-800 text-[11px]">월 저축여력</label>
                                    <input
                                        type="number"
                                        value={monthlySaving}
                                        onChange={(e) => setMonthlySaving(e.target.value)}
                                        className="w-full px-2.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl text-amber-950 font-bold text-xs"
                                    />
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
                                    <option value="KB">KB국민은행</option>
                                    <option value="SHINHAN">신한은행</option>
                                    <option value="WOORI">우리은행</option>
                                    <option value="HANA">하나은행</option>
                                    <option value="KAKAO">카카오뱅크</option>
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
                                <label className="block mb-1.5 text-amber-800">월 카드 예산 상한 (원)</label>
                                <input
                                    type="number"
                                    value={cardBudgetCap}
                                    onChange={(e) => setCardBudgetCap(e.target.value)}
                                    className="w-full px-4 py-3 bg-amber-100/40 border border-amber-300 rounded-2xl text-amber-950 font-black text-sm"
                                />
                            </div>

                            <Button
                                onClick={handleRunSimulation}
                                disabled={isLoading}
                                className="w-full py-4 text-base bg-amber-500 text-white font-black rounded-2xl hover:bg-amber-600 transition-colors shadow-md mt-4"
                            >
                                {isLoading ? "314개 상품 AI 분석 중... 🎲" : "⚡ 전체 상품 AI 시뮬레이션 실행"}
                            </Button>
                        </div>
                    </Card>
                </div>

                {/* 우측 시뮬레이션 결과 리스트 */}
                <div className="lg:col-span-7 space-y-6">
                    {resultData?.cardBudget && (
                        <div className="bg-amber-100/80 p-5 rounded-2xl border border-amber-300 flex justify-between items-center text-amber-950 text-xs font-bold">
                            <div>
                                📊 최근 6개월 카드 소비 분석:
                                <span className="text-amber-700 ml-1">
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
                            <p className="text-xs font-black text-amber-900">
                                🎯 총 {resultData.products.length}개 상품 진단 완료!
                            </p>
                            {resultData.products.slice(0, 10).map((prod, idx) => (
                                <Card key={prod.productId || idx} title={prod.productName} subtitle={`${prod.bankName || '은행'} | ${prod.termMonths || 12}개월`}>
                                    <div className="space-y-3">
                                        <div className="flex justify-between items-center p-3 bg-amber-50/80 rounded-xl border border-amber-200">
                                            <div>
                                                <p className="text-[10px] text-amber-700 font-bold">기본금리 → 최고금리</p>
                                                <p className="text-xs font-black text-amber-900">{prod.baseRate}% ~ {prod.maxRate}%</p>
                                            </div>
                                            <div className="text-right">
                                                <p className="text-[10px] text-amber-700 font-bold">굴리미 AI 기대금리 E[r]</p>
                                                <p className="text-lg font-black text-amber-600">연 {prod.expectedRate}% 🎉</p>
                                            </div>
                                        </div>
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
        </div>
    );
}