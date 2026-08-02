import React, { useState, useEffect, useCallback } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

const OPTIONS_STORAGE_KEY = 'gulimi_options_state';

export default function OptionsPage({ userProfile, onNext, onPrev, onGoSimulation }) {
    // 💡 [수정] 옛날 캐시 데이터 때문에 화면이 갱신되지 않는 현상 방지
    const [optionsData, setOptionsData] = useState(null);
    const [isLoading, setIsLoading] = useState(false);

    const fetchOptions = useCallback(async () => {
        if (!userProfile) return;
        setIsLoading(true);

        try {
            const response = await fetch('/api/v1/options', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Cache-Control': 'no-cache'
                },
                body: JSON.stringify({
                    profile: userProfile,
                    riskTolerance: 0.5,
                }),
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(`플랜 조회 실패: ${response.status} - ${errText}`);
            }

            const data = await response.json();
            setOptionsData(data);
            // 최신 응답 데이터로 저장소 업데이트
            localStorage.setItem(OPTIONS_STORAGE_KEY, JSON.stringify({ optionsData: data }));
        } catch (error) {
            console.error('Options API 연동 에러:', error);
        } finally {
            setIsLoading(false);
        }
    }, [userProfile]);

    useEffect(() => {
        if (userProfile) {
            fetchOptions();
        }
    }, [userProfile, fetchOptions]);

    const handleSelectOption = (option) => {
        localStorage.setItem('gulimi_selected_option', JSON.stringify(option));
        onNext?.(option);
    };

    if (!userProfile) {
        return (
            <div className="max-w-2xl mx-auto py-16 text-center space-y-6">
                <Card title="자산 AI 진단 필요" icon="⚠️">
                    <div className="py-10 space-y-4">
                        <p className="text-lg font-black text-amber-950">
                            아직 자산 시뮬레이션을 진행하지 않으셨어요!
                        </p>
                        <p className="text-xs text-amber-800/70 font-semibold">
                            1단계에서 자산 프로필을 먼저 입력하시고 AI 시뮬레이션을 돌려보세요.
                        </p>
                        <div className="pt-2">
                            <Button
                                onClick={onGoSimulation || onPrev}
                                className="px-8 py-3.5 bg-amber-500 text-white font-black text-sm rounded-2xl hover:bg-amber-600 shadow-md"
                            >
                                🎲 시뮬레이션 돌리러 가기
                            </Button>
                        </div>
                    </div>
                </Card>
            </div>
        );
    }

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto py-4">
            <div className="text-center space-y-2 py-2">
                <span className="bg-amber-200/70 text-amber-900 text-xs font-black px-3 py-1 rounded-full border border-amber-300/50">
                    ✨ Step 2. 굴리미 추천 플랜 선택
                </span>
                <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                    어떤 스타일로 굴려볼까요?
                </h1>
                <p className="text-amber-800/60 text-xs font-semibold">
                    몬테카를로 AI 시뮬레이션으로 조건 달성 확률을 진단하여 산출한 최적의 자금 스케줄입니다.
                </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4">
                {isLoading ? (
                    <div className="col-span-3 text-center py-16 bg-white rounded-3xl border border-amber-200 space-y-3">
                        <span className="text-3xl inline-block animate-bounce">🎲</span>
                        <p className="text-amber-900 font-black text-base">
                            AI 몬테카를로 시뮬레이션 기반 최적 플랜 산출 중...
                        </p>
                    </div>
                ) : optionsData?.options?.length > 0 ? (
                    optionsData.options.map((option, index) => {
                        const type = option.optionType || (index === 0 ? 'STABLE' : index === 1 ? 'BALANCED' : 'AGGRESSIVE');
                        const isBest = type === 'BALANCED' || index === 1;
                        const isExceeded = option.resourceUsage?.cardBudgetExceeded || option.resourceUsage?.cashBalanceExceeded;

                        const scheduleLabel = type === 'STABLE'
                            ? '풍차형 분산 · 기본 보장 금리 중심'
                            : type === 'AGGRESSIVE'
                                ? '즉시 몰빵형 · 표면 최고 금리 도전'
                                : 'AI 몬테카를로 예측 · 실제 기대수익 극대화';

                        const planTitle = type === 'STABLE'
                            ? '원금 보장 꼭꼭 플랜'
                            : type === 'AGGRESSIVE'
                                ? '최대 이자 도전 플랜'
                                : 'AI 가성비 최고 플랜';

                        const finalAmount = Math.floor((option.expectedFinalAmount || 0) / 10000);
                        const totalReturn = Math.floor((option.expectedTotalReturn || 0) / 10000);
                        const expectedRate = option.weightedExpectedRate ? option.weightedExpectedRate.toFixed(2) : '0.00';

                        return (
                            <div
                                key={type || index}
                                className={`rounded-[2.5rem] p-6 border transition-all relative flex flex-col justify-between ${
                                    isExceeded
                                        ? 'bg-gray-100/90 border-gray-300 opacity-60'
                                        : `bg-white border-amber-200 ${isBest ? 'shadow-xl shadow-amber-200/50 -translate-y-2 ring-2 ring-amber-300' : 'shadow-sm hover:border-amber-300'}`
                                }`}
                            >
                                {isBest && !isExceeded && (
                                    <div className="absolute -top-3.5 left-1/2 -translate-x-1/2 bg-amber-400 text-amber-950 font-black text-[11px] px-4 py-1 rounded-full shadow-sm border border-amber-200">
                                        👑 굴리미 AI 강력 추천
                                    </div>
                                )}

                                <div>
                                    <div className="flex justify-between items-center mb-3">
                                        <span className={`text-xs font-bold px-3 py-1 rounded-full ${
                                            type === 'STABLE'
                                                ? 'bg-blue-100 text-blue-800'
                                                : type === 'BALANCED'
                                                    ? 'bg-amber-300 text-amber-950 font-black'
                                                    : 'bg-rose-100 text-rose-800'
                                        }`}>
                                            {type === 'STABLE' ? '안정형' : type === 'AGGRESSIVE' ? '수익형' : '최적형'}
                                        </span>
                                        {isExceeded && (
                                            <span className="text-[10px] font-bold bg-rose-100 text-rose-700 px-2.5 py-0.5 rounded-full">
                                                🚫 예산 초과
                                            </span>
                                        )}
                                    </div>

                                    <h3 className="text-lg font-black text-amber-950 mb-1">
                                        {planTitle}
                                    </h3>
                                    <p className="text-[11px] font-extrabold text-emerald-700 mb-1">
                                        {scheduleLabel}
                                    </p>
                                    <p className="text-xs text-amber-700/60 mb-4 font-medium">
                                        최종 만기 {option.completionMonth || 12}개월차 예상 수령액
                                    </p>

                                    <div className="bg-amber-50/50 p-4 rounded-2xl mb-4 border border-amber-100 space-y-2">
                                        <span className="text-2xl font-black text-amber-950 block">
                                            {finalAmount.toLocaleString()}만원
                                        </span>
                                        <div className="pt-2 border-t border-amber-200/40 flex justify-between items-center text-xs">
                                            <span className="text-amber-700/70 font-semibold">
                                                기대 수익 +{totalReturn.toLocaleString()}만원
                                            </span>
                                            <span className="font-extrabold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-100">
                                                {type === 'BALANCED' ? 'AI 기대금리' : '금리'} {expectedRate}%
                                            </span>
                                        </div>
                                    </div>

                                    <div className="space-y-2 mb-5">
                                        {(option.allocations || []).map((allocation, aIdx) => {
                                            const startMonthDisplay = allocation.startMonth === 0
                                                ? '1개월차(즉시)'
                                                : `${(allocation.startMonth || 0) + 1}개월차`;

                                            const monthlyAmt = Math.floor((allocation.monthlyAmount || 0) / 10000);
                                            const totalAmt = Math.floor((allocation.amount || 0) / 10000);

                                            return (
                                                <div key={allocation.slotIndex ?? aIdx} className="rounded-xl border border-amber-100 bg-white p-3 text-[11px] shadow-2xs">
                                                    <div className="flex justify-between font-extrabold text-amber-950 mb-0.5">
                                                        <span className="text-amber-900 font-black">
                                                            🗓️ {startMonthDisplay} · {allocation.productName || '추천 상품'}
                                                        </span>
                                                        <span className="text-emerald-700 font-black">
                                                            {monthlyAmt > 0
                                                                ? `월 ${monthlyAmt.toLocaleString()}만원`
                                                                : `${totalAmt.toLocaleString()}만원`}
                                                        </span>
                                                    </div>
                                                    <div className="flex justify-between text-[10px] text-amber-700/70 pt-1 border-t border-amber-50">
                                                        <span>{allocation.bankName || '금융사'}</span>
                                                        <span>만기 {allocation.maturityMonth || 12}개월차</span>
                                                    </div>
                                                    {allocation.warningMessage && (
                                                        <p className="mt-2 rounded-lg bg-blue-50 px-2 py-1.5 text-[10px] font-bold text-blue-700">
                                                            {allocation.warningMessage}
                                                        </p>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>

                                <Button
                                    variant={isBest ? 'primary' : 'secondary'}
                                    onClick={() => handleSelectOption(option)}
                                    disabled={isExceeded}
                                    className={isExceeded ? 'opacity-50 cursor-not-allowed bg-gray-200 text-gray-500 border-gray-300' : ''}
                                >
                                    {isExceeded ? '선택 불가 (예산 초과)' : '이 플랜 로드맵 보기 🚀'}
                                </Button>
                            </div>
                        );
                    })
                ) : (
                    <div className="col-span-3 text-center py-16 bg-white rounded-3xl border border-amber-200 space-y-3">
                        <p className="text-amber-900 font-black text-base">
                            추천 플랜을 불러올 수 없습니다. 다시 시도해 주세요.
                        </p>
                    </div>
                )}
            </div>

            {onPrev && (
                <div className="flex justify-start pt-4">
                    <button
                        onClick={onPrev}
                        className="px-6 py-3 bg-amber-100 hover:bg-amber-200 text-amber-950 font-bold rounded-2xl transition-all text-xs flex items-center gap-1"
                    >
                        👈 이전 단계(프로필 수정)로 돌아가기
                    </button>
                </div>
            )}
        </div>
    );
}
