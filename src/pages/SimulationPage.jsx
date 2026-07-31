import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function SimulationPage({ onNext }) {
    // ===== 기존 State 및 로직 100% 유지 =====
    const [employment, setEmployment] = useState("FULL_TIME");
    const [salaryTransferable, setSalaryTransferable] = useState(true);
    const [lumpSum, setLumpSum] = useState(20000000);
    const [emergencyFund, setEmergencyFund] = useState(3000000);
    const [monthlySaving, setMonthlySaving] = useState(1000000);
    const [targetMonths, setTargetMonths] = useState(12);
    const [cardSpend6m, setCardSpend6m] = useState([250000, 300000, 200000, 280000, 320000, 220000]);
    const [cardBudgetCap, setCardBudgetCap] = useState(300000);
    const [existingBank, setExistingBank] = useState("KB");

    const [isLoading, setIsLoading] = useState(false);
    const [resultData, setResultData] = useState(null);

    // 모바일 뷰 전용 Active Tab (입력 폼 / 진단 결과)
    const [activeTab, setActiveTab] = useState('input');

    const handleCardSpendChange = (index, value) => {
        const updated = [...cardSpend6m];
        updated[index] = Number(value) || 0;
        setCardSpend6m(updated);
    };

    const handleRunSimulation = async () => {
        setIsLoading(true);

        const profilePayload = {
            employment: employment,
            salaryTransferable: Boolean(salaryTransferable),
            lumpSum: Math.max(0, Number(lumpSum)),
            emergencyFund: Math.max(0, Number(emergencyFund)),
            monthlySaving: Math.max(0, Number(monthlySaving)),
            targetMonths: Number(targetMonths) || 12,
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

            // 데이터 수신 시 결과 탭으로 자동 이동
            setActiveTab('result');

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
        /* 스마트폰 화면비 고정 (Max-Width 440px / Mobile Container) */
        <div className="max-w-[430px] mx-auto min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between shadow-2xl relative border-x border-slate-200">

            {/* 상단 모바일 앱 헤더 */}
            <header className="sticky top-0 z-30 bg-white/90 backdrop-blur-md border-b border-slate-100 px-5 py-3.5 flex items-center justify-between">
                <div className="flex items-center space-x-2">
                    <span className="text-xl">🐣</span>
                    <span className="font-extrabold text-amber-950 text-lg tracking-tight">굴리미 AI</span>
                </div>
                <span className="text-[11px] font-black bg-amber-100 text-amber-800 px-2.5 py-1 rounded-full">
                    Step 1. 진단
                </span>
            </header>

            {/* 모바일 메인 스크롤 영역 */}
            <main className="flex-1 overflow-y-auto px-4 pt-4 pb-28">
                {/* 메인 타이틀 */}
                <div className="mb-4">
                    <h1 className="text-xl font-black text-amber-950 tracking-tight leading-snug">
                        내 자산 데이터로<br />최상의 금리를 진단해 보세요
                    </h1>
                </div>

                {/* 모바일 탭 스위처 */}
                <div className="flex bg-slate-200/70 p-1 rounded-xl mb-4 text-xs font-bold">
                    <button
                        onClick={() => setActiveTab('input')}
                        className={`flex-1 py-2 rounded-lg transition-all ${
                            activeTab === 'input'
                                ? 'bg-white text-amber-950 shadow-sm font-black'
                                : 'text-slate-500'
                        }`}
                    >
                        👤 프로필 설정
                    </button>
                    <button
                        onClick={() => setActiveTab('result')}
                        className={`flex-1 py-2 rounded-lg transition-all flex items-center justify-center gap-1 ${
                            activeTab === 'result'
                                ? 'bg-white text-amber-950 shadow-sm font-black'
                                : 'text-slate-500'
                        }`}
                    >
                        📊 진단 결과
                        {resultData?.products && (
                            <span className="bg-amber-500 text-white text-[9px] px-1.5 py-0.5 rounded-full">
                                {resultData.products.length}
                            </span>
                        )}
                    </button>
                </div>

                {/* TAB 1: 프로필 입력 폼 */}
                {activeTab === 'input' && (
                    <div className="space-y-4">
                        <Card title="기본 자산 프로필" icon="📝">
                            <div className="space-y-3 text-xs">
                                {/* 근로 형태 & 급여이체 */}
                                <div className="grid grid-cols-2 gap-2">
                                    <div>
                                        <label className="block text-[11px] font-bold text-slate-600 mb-1">근로 형태</label>
                                        <select
                                            value={employment}
                                            onChange={(e) => setEmployment(e.target.value)}
                                            className="w-full px-3 py-2.5 bg-slate-100 border border-slate-200 rounded-xl font-bold text-slate-800"
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
                                        <label className="block text-[11px] font-bold text-slate-600 mb-1">급여이체 가능</label>
                                        <select
                                            value={salaryTransferable ? "Y" : "N"}
                                            onChange={(e) => setSalaryTransferable(e.target.value === "Y")}
                                            className="w-full px-3 py-2.5 bg-slate-100 border border-slate-200 rounded-xl font-bold text-slate-800"
                                        >
                                            <option value="Y">가능 (Y)</option>
                                            <option value="N">불가능 (N)</option>
                                        </select>
                                    </div>
                                </div>

                                {/* 목표 저축 기간 */}
                                <div>
                                    <label className="block text-[11px] font-bold text-amber-900 mb-1">🎯 목표 저축 기간</label>
                                    <select
                                        value={targetMonths}
                                        onChange={(e) => setTargetMonths(Number(e.target.value))}
                                        className="w-full px-3 py-2.5 bg-amber-50 border border-amber-300 rounded-xl text-amber-950 font-black"
                                    >
                                        <option value={6}>6개월 (단기 굴리기)</option>
                                        <option value={12}>12개월 (1년 표준)</option>
                                        <option value={24}>24개월 (2년 장기)</option>
                                        <option value={36}>36개월 (3년 목돈)</option>
                                    </select>
                                </div>

                                {/* 자산 내역 (보유 목돈 / 비상금 / 월 저축) */}
                                <div className="space-y-2 pt-1">
                                    <div>
                                        <label className="block text-[11px] font-bold text-slate-600 mb-1">보유 목돈 (원)</label>
                                        <input
                                            type="number"
                                            value={lumpSum}
                                            onChange={(e) => setLumpSum(e.target.value)}
                                            className="w-full px-3 py-2 bg-slate-100 border border-slate-200 rounded-xl font-extrabold text-slate-900"
                                        />
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <div>
                                            <label className="block text-[11px] font-bold text-slate-600 mb-1">비상금 (원)</label>
                                            <input
                                                type="number"
                                                value={emergencyFund}
                                                onChange={(e) => setEmergencyFund(e.target.value)}
                                                className="w-full px-3 py-2 bg-slate-100 border border-slate-200 rounded-xl font-bold text-slate-900"
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-[11px] font-bold text-slate-600 mb-1">월 저축 여력 (원)</label>
                                            <input
                                                type="number"
                                                value={monthlySaving}
                                                onChange={(e) => setMonthlySaving(e.target.value)}
                                                className="w-full px-3 py-2 bg-slate-100 border border-slate-200 rounded-xl font-bold text-slate-900"
                                            />
                                        </div>
                                    </div>
                                </div>

                                {/* 주거래 은행 */}
                                <div>
                                    <label className="block text-[11px] font-bold text-slate-600 mb-1">주거래 은행</label>
                                    <select
                                        value={existingBank}
                                        onChange={(e) => setExistingBank(e.target.value)}
                                        className="w-full px-3 py-2.5 bg-slate-100 border border-slate-200 rounded-xl font-bold text-slate-800"
                                    >
                                        <option value="KB">KB국민은행</option>
                                        <option value="SHINHAN">신한은행</option>
                                        <option value="WOORI">우리은행</option>
                                        <option value="HANA">하나은행</option>
                                        <option value="KAKAO">카카오뱅크</option>
                                    </select>
                                </div>
                            </div>
                        </Card>

                        {/* 카드 소비 데이터 */}
                        <Card title="카드 소비 패턴" icon="💳">
                            <div className="space-y-3 text-xs">
                                <div>
                                    <label className="block text-[11px] font-bold text-slate-600 mb-1.5">최근 6개월 카드 사용액</label>
                                    <div className="grid grid-cols-3 gap-1.5">
                                        {cardSpend6m.map((spend, idx) => (
                                            <div key={idx} className="bg-slate-100 p-1.5 rounded-lg border border-slate-200">
                                                <span className="block text-[9px] font-bold text-slate-400">{idx + 1}개월 전</span>
                                                <input
                                                    type="number"
                                                    value={spend}
                                                    onChange={(e) => handleCardSpendChange(idx, e.target.value)}
                                                    className="w-full bg-transparent font-bold text-slate-800 focus:outline-none text-xs"
                                                />
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-[11px] font-bold text-slate-600 mb-1">월 카드 예산 상한 (원)</label>
                                    <input
                                        type="number"
                                        value={cardBudgetCap}
                                        onChange={(e) => setCardBudgetCap(e.target.value)}
                                        className="w-full px-3 py-2 bg-amber-50 border border-amber-200 rounded-xl font-black text-amber-950 text-xs"
                                    />
                                </div>
                            </div>
                        </Card>
                    </div>
                )}

                {/* TAB 2: 진단 결과 */}
                {activeTab === 'result' && (
                    <div className="space-y-4">
                        {resultData?.cardBudget && (
                            <div className="bg-amber-100/90 p-3.5 rounded-2xl border border-amber-300 text-amber-950 text-xs">
                                <div className="flex justify-between items-center mb-1">
                                    <span className="font-extrabold">📊 최근 6개월 카드 분석</span>
                                    <span className="bg-amber-500 text-white px-2 py-0.5 rounded-full text-[9px] font-black">
                                        {resultData.cardBudget.distribution || "NORMAL"}
                                    </span>
                                </div>
                                <p className="text-[11px] text-amber-800">
                                    평균 <span className="font-bold">{resultData.cardBudget.mean?.toLocaleString()}원</span> (표준편차: {resultData.cardBudget.std?.toLocaleString()}원)
                                </p>
                            </div>
                        )}

                        {resultData?.products && resultData.products.length > 0 ? (
                            <div className="space-y-3">
                                <p className="text-xs font-black text-amber-950 px-1">
                                    🎯 AI 추천 Top {Math.min(10, resultData.products.length)} 상품
                                </p>
                                {resultData.products.slice(0, 10).map((prod, idx) => (
                                    <div key={prod.productId || idx} className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm space-y-2">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <span className="text-[10px] font-extrabold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-md">
                                                    {prod.bankName || '시중은행'}
                                                </span>
                                                <h3 className="font-black text-slate-900 text-sm mt-1">{prod.productName}</h3>
                                            </div>
                                            <span className="text-[11px] font-bold text-slate-400">
                                                {prod.termMonths || 12}개월
                                            </span>
                                        </div>

                                        <div className="flex justify-between items-center pt-2 border-t border-slate-100 mt-2">
                                            <div>
                                                <p className="text-[10px] text-slate-400 font-bold">기본 → 최고</p>
                                                <p className="text-xs font-bold text-slate-600">{prod.baseRate}% ~ {prod.maxRate}%</p>
                                            </div>
                                            <div className="text-right">
                                                <p className="text-[10px] text-amber-700 font-bold">AI 기대금리</p>
                                                <p className="text-base font-black text-amber-500">연 {prod.expectedRate}%</p>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="bg-white rounded-2xl p-8 text-center border border-slate-200 space-y-2 my-6">
                                <span className="text-4xl block">🎲</span>
                                <p className="font-bold text-slate-700 text-sm">AI 진단 대기 중</p>
                                <p className="text-xs text-slate-400">
                                    아래 버튼을 눌러 314개 전체 상품의 금리 시뮬레이션을 실행해 보세요!
                                </p>
                            </div>
                        )}
                    </div>
                )}
            </main>

            {/* 하단 고정 액션 버튼 (Mobile Bottom Sticky Bar) */}
            <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] p-4 bg-white/90 backdrop-blur-md border-t border-slate-200 z-40">
                <Button
                    onClick={handleRunSimulation}
                    disabled={isLoading}
                    className="w-full py-3.5 text-sm bg-amber-500 hover:bg-amber-600 text-white font-black rounded-2xl transition-all shadow-lg shadow-amber-500/20 active:scale-[0.98]"
                >
                    {isLoading ? "314개 상품 AI 분석 중... 🎲" : "⚡ 전체 상품 AI 시뮬레이션 실행"}
                </Button>
            </div>
        </div>
    );
}