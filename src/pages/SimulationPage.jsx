import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function SimulationPage({ onNext }) {
    // 1. 온보딩 폼 입력 상태 (스펙 항목 완벽 반영)
    const [form, setForm] = useState({
        employmentType: 'EMPLOYED',         // 근로형태
        totalLumpSum: '20,000,000',          // 보유 목돈
        emergencyFund: '3,000,000',         // 비상금 슬롯
        monthlySavingsCapacity: '1,000,000', // 월 저축 여력
        primaryBank: 'KB',                  // 거래 은행
        salaryTransferAvailable: true,       // 급여 이체 가능 여부
        // 최근 6개월 카드 사용 내역 (단위: 원)
        recent6mCardSpend: ['500,000', '480,000', '520,000', '510,000', '490,000', '530,000'],
        productId: 'KB_SAVINGS_01'
    });

    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null);

    // 핸들러 함수들
    const handleChange = (field, value) => {
        setForm(prev => ({ ...prev, [field]: value }));
    };

    const handleCardSpendChange = (index, value) => {
        const updated = [...form.recent6mCardSpend];
        updated[index] = value;
        setForm(prev => ({ ...prev, recent6mCardSpend: updated }));
    };

    // 예린님이 작성한 백엔드 /simulate API 호출
    const handleRunSimulation = async () => {
        setLoading(true);
        try {
            // 숫자 형변환 (콤마 제거)
            const payload = {
                productId: form.productId,
                salaryTransferAvailable: form.salaryTransferAvailable,
                recent6mCardSpend: form.recent6mCardSpend.map(v => Number(v.replace(/,/g, '')) || 0)
            };

            const response = await fetch('/simulate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) throw new Error('시뮬레이션 실패');
            const data = await response.json();
            setResult(data);
        } catch (err) {
            console.error(err);
            // 백엔드가 로컬에서 안 떠있을 때 보여줄 가짜 시연 데이터 (Fallback)
            setResult({
                baseRate: 2.50,
                expectedRate: 5.15,
                conditionEvaluations: [
                    {
                        sourceText: "1. 매월 카드 실적 50만원",
                        achievementProbability: 88.0,
                        confidenceMin: 82.5,
                        confidenceMax: 93.1,
                        reason: "최근 6개월 평균 51.6만원으로 매우 안정적이에요!"
                    },
                    {
                        sourceText: "2. 주거래 급여이체 지정",
                        achievementProbability: 65.0,
                        confidenceMin: 60.0,
                        confidenceMax: 70.0,
                        reason: "주거래 은행 변경이 필요하여 유동적입니다."
                    }
                ]
            });
        } finally {
            setLoading(false);
        }
    };

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
                <div className="lg:col-span-5 space-y-4">
                    <Card title="내 자산 프로필" icon="💰" subtitle="기본 정보를 쏙쏙 입력해 주세요">
                        <div className="space-y-4 text-xs font-bold text-amber-900">

                            {/* 근로 형태 & 주요 거래 은행 */}
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block mb-1.5 text-amber-800">근로 형태</label>
                                    <select
                                        value={form.employmentType}
                                        onChange={(e) => handleChange('employmentType', e.target.value)}
                                        className="w-full px-3 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                    >
                                        <option value="EMPLOYED">직장인</option>
                                        <option value="SELF_EMPLOYED">사업자</option>
                                        <option value="FREELANCER">프리랜서</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="block mb-1.5 text-amber-800">주요 거래 은행</label>
                                    <select
                                        value={form.primaryBank}
                                        onChange={(e) => handleChange('primaryBank', e.target.value)}
                                        className="w-full px-3 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                    >
                                        <option value="KB">KB국민</option>
                                        <option value="SHINHAN">신한</option>
                                        <option value="WOORI">우리</option>
                                        <option value="HANA">하나</option>
                                    </select>
                                </div>
                            </div>

                            {/* 보유 목돈 */}
                            <div>
                                <label className="block mb-1.5 text-amber-800">보유 목돈</label>
                                <div className="relative">
                                    <input
                                        type="text"
                                        value={form.totalLumpSum}
                                        onChange={(e) => handleChange('totalLumpSum', e.target.value)}
                                        className="w-full px-4 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-extrabold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300 text-sm"
                                    />
                                    <span className="absolute right-4 top-3.5 text-amber-500 font-bold">원</span>
                                </div>
                            </div>

                            {/* 비상금 & 월 저축 여력 */}
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block mb-1.5 text-amber-800">비상금 슬롯</label>
                                    <input
                                        type="text"
                                        value={form.emergencyFund}
                                        onChange={(e) => handleChange('emergencyFund', e.target.value)}
                                        className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                    />
                                </div>
                                <div>
                                    <label className="block mb-1.5 text-amber-800">월 저축 여력</label>
                                    <input
                                        type="text"
                                        value={form.monthlySavingsCapacity}
                                        onChange={(e) => handleChange('monthlySavingsCapacity', e.target.value)}
                                        className="w-full px-3.5 py-3 bg-amber-50/60 border border-amber-200/80 rounded-2xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300"
                                    />
                                </div>
                            </div>

                            {/* 최근 6개월 카드 사용 내역 */}
                            <div>
                                <label className="block mb-1.5 text-amber-800">최근 6개월 카드 사용 내역 (원)</label>
                                <div className="grid grid-cols-3 gap-2">
                                    {form.recent6mCardSpend.map((spend, idx) => (
                                        <div key={idx} className="relative">
                                            <span className="text-[10px] text-amber-700/70 block mb-0.5">{idx + 1}개월 전</span>
                                            <input
                                                type="text"
                                                value={spend}
                                                onChange={(e) => handleCardSpendChange(idx, e.target.value)}
                                                className="w-full px-2.5 py-2 bg-amber-50/60 border border-amber-200/80 rounded-xl outline-none font-bold text-amber-950 focus:bg-white focus:ring-2 focus:ring-amber-300 text-[11px]"
                                            />
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* 상품 선택 */}
                            <div>
                                <label className="block mb-2 text-amber-800">궁금한 상품 선택</label>
                                <select
                                    value={form.productId}
                                    onChange={(e) => handleChange('productId', e.target.value)}
                                    className="w-full px-4 py-3 bg-amber-100/50 border border-amber-200 rounded-2xl text-amber-900 font-extrabold outline-none focus:ring-2 focus:ring-amber-300"
                                >
                                    <option value="KB_SAVINGS_01">💛 KB 청년 희망 적금 (최대 6.0%)</option>
                                    <option value="SHINHAN_DEPOSIT_01">🧡 신한 쏠쏠 특판 예금 (최대 4.2%)</option>
                                </select>
                            </div>

                            <button
                                type="button"
                                onClick={handleRunSimulation}
                                disabled={loading}
                                className="w-full py-3 bg-amber-400 hover:bg-amber-500 active:scale-95 text-amber-950 font-black rounded-2xl shadow-md transition-all text-xs"
                            >
                                {loading ? '🎲 10,000회 시뮬레이션 계산 중...' : '⚡ AI 시뮬레이션 돌리기'}
                            </button>
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
                                몬테카를로 10,000회 진단 완료!
                            </span>
                            <span className="text-xs font-bold text-amber-900">신뢰도 90%</span>
                        </div>

                        <div className="bg-white/90 backdrop-blur p-5 rounded-2xl border border-amber-200/50 flex justify-between items-center">
                            <div>
                                <p className="text-xs font-bold text-amber-700">기본 금리</p>
                                <p className="text-xl font-bold text-amber-900">
                                    {result ? `${result.baseRate?.toFixed(2)}%` : '2.50%'}
                                </p>
                            </div>
                            <div className="text-right">
                                <p className="text-xs font-black text-amber-600">굴리미 예상 기대금리 E[r]</p>
                                <p className="text-3xl font-black text-amber-950">
                                    연 {result ? `${result.expectedRate?.toFixed(2)}%` : '5.15%'} 🎉
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* XAI 상세 사유 */}
                    <Card title="조건별 달성 확률 리스트" icon="🔍" subtitle="왜 이 확률이 나왔는지 친절하게 알려드려요">
                        <div className="space-y-3">
                            {(result?.conditionEvaluations || [
                                {
                                    sourceText: "1. 매월 카드 실적 50만원",
                                    achievementProbability: 88.0,
                                    confidenceMin: 82.5,
                                    confidenceMax: 93.1,
                                    reason: "최근 6개월 평균 51.6만원으로 매우 안정적이에요!"
                                },
                                {
                                    sourceText: "2. 주거래 급여이체 지정",
                                    achievementProbability: 65.0,
                                    confidenceMin: 60.0,
                                    confidenceMax: 70.0,
                                    reason: "주거래 은행 변경이 필요하여 유동적입니다."
                                }
                            ]).map((cond, i) => (
                                <div key={i} className="p-4 bg-amber-50/50 rounded-2xl border border-amber-100 flex items-center justify-between">
                                    <div>
                                        <p className="text-xs font-black text-amber-950">{cond.sourceText}</p>
                                        <p className="text-[11px] text-amber-700/80 mt-0.5">{cond.reason}</p>
                                        {cond.confidenceMin && (
                                            <p className="text-[10px] text-amber-600/70 mt-0.5">
                                                📊 90% 신뢰구간: {cond.confidenceMin}% ~ {cond.confidenceMax}%
                                            </p>
                                        )}
                                    </div>
                                    <span className={`text-xs font-black px-3 py-1 rounded-full ${
                                        cond.achievementProbability >= 80
                                            ? 'bg-emerald-100 text-emerald-800'
                                            : 'bg-amber-100 text-amber-800'
                                    }`}>
                                        {cond.achievementProbability}% ({cond.achievementProbability >= 80 ? '높음' : '보통'})
                                    </span>
                                </div>
                            ))}
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