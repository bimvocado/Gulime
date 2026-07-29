import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function SimulationPage({ onNext }) {
    // 1️⃣ 백엔드 ProfileRequest 스펙에 맞춘 State 들
    const [lumpSum, setLumpSum] = useState(20000000);
    const [emergencyFund, setEmergencyFund] = useState(3000000);
    const [monthlySaving, setMonthlySaving] = useState(1000000);
    const [cardBudgetCap, setCardBudgetCap] = useState(500000);
    const [selectedProduct, setSelectedProduct] = useState("KB_YOUTH");

    // 2️⃣ 백엔드 응답(SimulateResponse) 데이터 및 로딩 State
    const [resultData, setResultData] = useState(null);
    const [isLoading, setIsLoading] = useState(false);

    // 3️⃣ 백엔드 API 연동 함수
    const handleRunSimulation = async () => {
        setIsLoading(true);
        try {
            const response = await fetch('/api/v1/simulate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    profile: {
                        employment: "FULL_TIME", // 기본값 (백엔드 EmploymentType enum)
                        salaryTransferable: true,
                        lumpSum: Number(lumpSum),
                        emergencyFund: Number(emergencyFund),
                        monthlySaving: Number(monthlySaving),
                        // cardSpend6m 은 6개 원소가 필수 (@Size(min=6, max=6))
                        cardSpend6m: [
                            Number(cardBudgetCap), Number(cardBudgetCap), Number(cardBudgetCap),
                            Number(cardBudgetCap), Number(cardBudgetCap), Number(cardBudgetCap)
                        ],
                        cardBudgetCap: Number(cardBudgetCap),
                        existingBanks: ["KB"] // 기본 이용 은행
                    },
                    productIds: [selectedProduct] // 선택한 상품 ID 리스트
                }),
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(`시뮬레이션 실패: ${response.status} - ${errText}`);
            }

            const data = await response.json();
            console.log("백엔드 응답 성공 데이터 (SimulateResponse):", data);

            // 받아온 결과 저장해서 오른쪽 UI에 바인딩
            setResultData(data);

        } catch (error) {
            console.error("API 연동 에러:", error);
            alert("시뮬레이션 호출 중 에러가 발생했습니다. 개발자 도구 콘솔을 확인해 보세요!");
        } finally {
            setIsLoading(false);
        }
    };

    // 첫 번째 응답 상품 추출 (결과 표시용)
    const firstProduct = resultData?.products?.[0];

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto">
            {/* 타이틀 헤더 */}
            <div className="text-center space-y-2 py-4">
                <span className="bg-amber-200/70 text-amber-900 text-xs font-black px-3 py-1 rounded-full border border-amber-300/50">
                  🐣 Step 1. 내 지갑 상태 알려주기
                </span>
                <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                    얼마를 굴려볼까요?
                </h1>
                <p className="text-amber-800/60 text-xs font-semibold">
                    굴리미 AI가 10,000번 시뮬레이션해서 우대금리 받을 확률을 맞춰볼게요!
                </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
                {/* 왼쪽: 프로필 입력 폼 */}
                <div className="lg:col-span-5">
                    <Card title="내 자산 프로필" icon="💰" subtitle="기본 정보를 쏙쏙 입력해 주세요">
                        <div className="space-y-4 text-xs font-bold text-amber-900">
                            <div>
                                <label className="block mb-1.5 text-amber-800">보유 목돈 (원)</label>
                                <input
                                    type="number"
                                    value={lumpSum}
                                    onChange={(e) => setLumpSum(e.target.value)}
                                    className="w-full px-4 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-extrabold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300 text-sm"
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block mb-1.5 text-amber-800">비상금 슬롯</label>
                                    <input
                                        type="number"
                                        value={emergencyFund}
                                        onChange={(e) => setEmergencyFund(e.target.value)}
                                        className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                    />
                                </div>
                                <div>
                                    <label className="block mb-1.5 text-amber-800">월 저축 여력</label>
                                    <input
                                        type="number"
                                        value={monthlySaving}
                                        onChange={(e) => setMonthlySaving(e.target.value)}
                                        className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                    />
                                </div>
                            </div>

                            <div>
                                <label className="block mb-1.5 text-amber-800">월 카드 예산 상한</label>
                                <input
                                    type="number"
                                    value={cardBudgetCap}
                                    onChange={(e) => setCardBudgetCap(e.target.value)}
                                    className="w-full px-4 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                />
                            </div>

                            <div>
                                <label className="block mb-2 text-amber-800">궁금한 상품 선택</label>
                                <select
                                    value={selectedProduct}
                                    onChange={(e) => setSelectedProduct(e.target.value)}
                                    className="w-full px-4 py-3 bg-amber-100/50 border border-amber-200 rounded-2xl text-amber-900 font-extrabold outline-none focus:ring-2 focus:ring-amber-300"
                                >
                                    <option value="KB_YOUTH">💛 KB 청년 희망 적금 (최대 6.0%)</option>
                                    <option value="SHINHAN_SOL">🧡 신한 쏠쏠 특판 예금 (최대 4.2%)</option>
                                </select>
                            </div>

                            {/* ⚡ 시뮬레이션 돌리기 버튼 */}
                            <Button
                                onClick={handleRunSimulation}
                                disabled={isLoading}
                                className="w-full py-3 bg-amber-500 text-white font-black rounded-xl hover:bg-amber-600 transition-colors shadow-md"
                            >
                                {isLoading ? "10,000번 진단 중... 🎲" : "⚡ AI 시뮬레이션 실행"}
                            </Button>
                        </div>
                    </Card>
                </div>

                {/* 오른쪽: 진단 및 시뮬레이션 결과 */}
                <div className="lg:col-span-7 space-y-6">
                    {/* 하이라이트 결과 카드 */}
                    <div className="bg-amber-300/80 p-7 rounded-[2.5rem] border border-amber-300 shadow-lg shadow-amber-200/50 relative overflow-hidden">
                        <div className="absolute -right-4 -bottom-4 text-8xl opacity-20 pointer-events-none">🎲</div>

                        <div className="flex justify-between items-center mb-4">
                            <span className="text-xs font-black bg-amber-950 text-amber-300 px-3 py-1 rounded-full">
                                {resultData ? "몬테카를로 10,000회 진단 완료!" : "시뮬레이션 대기 중"}
                            </span>
                            <span className="text-xs font-bold text-amber-900">신뢰도 90%</span>
                        </div>

                        <div className="bg-white/90 backdrop-blur p-5 rounded-2xl border border-amber-200/50 flex justify-between items-center">
                            <div>
                                <p className="text-xs font-bold text-amber-700">기본 금리</p>
                                <p className="text-xl font-bold text-amber-900">
                                    {firstProduct ? `${firstProduct.baseRate}%` : "2.50%"}
                                </p>
                            </div>
                            <div className="text-right">
                                <p className="text-xs font-black text-amber-600">굴리미 예상 기대금리 E[r]</p>
                                <p className="text-3xl font-black text-amber-950">
                                    {firstProduct ? `연 ${firstProduct.expectedRate}% 🎉` : "연 5.15% 🎉"}
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* XAI 상세 사유 */}
                    <Card title="조건별 달성 확률 리스트" icon="🔍" subtitle="왜 이 확률이 나왔는지 친절하게 알려드려요">
                        <div className="space-y-3">
                            <div className="p-4 bg-amber-50/50 rounded-2xl border border-amber-100 flex items-center justify-between">
                                <div>
                                    <p className="text-xs font-black text-amber-950">1. 카드 실적 구간 진단</p>
                                    <p className="text-[11px] text-amber-700/80 mt-0.5">
                                        {resultData?.cardBudget ? "카드 적정 예산 분석 완료!" : "최근 6개월 평균 기반 분석 준비 완료"}
                                    </p>
                                </div>
                                <span className="text-xs font-black bg-emerald-100 text-emerald-800 px-3 py-1 rounded-full">
                                    {resultData ? "달성 완료" : "88% (높음)"}
                                </span>
                            </div>
                        </div>
                    </Card>

                    <Button onClick={onNext} className="w-full text-base py-4">
                        나한테 꼭 맞는 3가지 플랜 보러가기 🚀
                    </Button>
                </div>
            </div>
        </div>
    );
}