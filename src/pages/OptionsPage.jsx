import React, { useState, useEffect, useCallback } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function OptionsPage({ userProfile, onNext, onPrev }) {
    // 1. 리스크 슬라이더 상태 (0 ~ 100)
    const [riskTolerance, setRiskTolerance] = useState(50);
    const [optionsData, setOptionsData] = useState(null);
    const [isLoading, setIsLoading] = useState(false);

    // 2. 백엔드 /api/v1/options 호출 함수
    const fetchOptions = useCallback(async (riskVal) => {
        setIsLoading(true);

        // 프로필이 없는 경우 기본 예시 데이터 구조 사용 방어 코드
        const profile = userProfile || {
            employment: "FULL_TIME",
            salaryTransferable: true,
            lumpSum: 20000000,
            emergencyFund: 3000000,
            monthlySaving: 1000000,
            targetMonths: 12,
            cardSpend6m: [250000, 300000, 200000, 280000, 320000, 220000],
            cardBudgetCap: 300000,
            existingBanks: []
        };

        const requestBody = {
            profile: profile,
            riskTolerance: riskVal / 100.0 // 0~100 정수를 0.0~1.0 소수로 변환 (백엔드 @DecimalMin/@DecimalMax)
        };

        try {
            const response = await fetch('/api/v1/options', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody),
            });

            if (!response.ok) {
                const errText = await response.text();
                throw new Error(`플랜 조회 실패: ${response.status} - ${errText}`);
            }

            const data = await response.json();
            console.log("백엔드 Options 응답 성공:", data);
            setOptionsData(data);
        } catch (error) {
            console.error("Options API 연동 에러:", error);
        } finally {
            setIsLoading(false);
        }
    }, [userProfile]);

    // 슬라이더 변경 시 백엔드 재요청 (Debounce 효과 적용)
    useEffect(() => {
        const timer = setTimeout(() => {
            fetchOptions(riskTolerance);
        }, 300);
        return () => clearTimeout(timer);
    }, [riskTolerance, fetchOptions]);

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto py-4">
            {/* 헤더 */}
            <div className="text-center space-y-2 py-2">
                <span className="bg-amber-200/70 text-amber-900 text-xs font-black px-3 py-1 rounded-full border border-amber-300/50">
                  ✨ Step 2. 굴리미 추천 플랜 선택
                </span>
                <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                    어떤 스타일로 굴려볼까요?
                </h1>
                <p className="text-amber-800/60 text-xs font-semibold">
                    자원 제약을 꼼꼼히 계산해 도출한 파레토 최적 조합입니다.
                </p>
            </div>

            {/* 🎚️ 리스크 조절 슬라이더 */}
            <Card title="내 리스크 민감도 조절" icon="🎚️" subtitle="슬라이더를 움직여 달성 확률과 목표 수익 사이의 균형을 맞추세요">
                <div className="space-y-3 py-1">
                    <div className="flex justify-between items-center text-xs font-extrabold text-amber-950">
                        <span className="text-blue-700">🛡️ 안전 우대 (확률 위주)</span>
                        <span className="bg-amber-200/80 px-3 py-1 rounded-full text-amber-900">
                            위험 선호도: {riskTolerance}% {isLoading && "🔄 분석 중..."}
                        </span>
                        <span className="text-rose-700">🔥 수익 우대 (금리 위주)</span>
                    </div>
                    <input
                        type="range"
                        min="0"
                        max="100"
                        value={riskTolerance}
                        onChange={(e) => setRiskTolerance(Number(e.target.value))}
                        className="w-full accent-amber-500 h-2.5 bg-amber-100 rounded-lg cursor-pointer"
                    />
                </div>
            </Card>

                    <div className="flex justify-between items-center text-[10px] font-bold text-slate-400">
                        <span className="text-blue-600">🛡️ 안전 우대</span>
                        <span className="text-rose-600">🔥 수익 우대</span>
                    </div>
                </div>
            </Card>

            {/* 3가지 카드 선택지 */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {optionsData?.options && optionsData.options.length > 0 ? (
                    optionsData.options.map((opt, idx) => {
                        const isBest = idx === 1; // 가운데(균형형) 플랜을 대표 추천으로 표기
                        const isExceeded = opt.isBudgetExceeded || opt.exceeded || false;
                        const scheduleLabel = opt.optionType === 'STABLE'
                            ? '월별 풍차형 · 1개월 간격 가입'
                            : opt.optionType === 'AGGRESSIVE'
                                ? '즉시 분산형 · 첫 달 모두 가입'
                                : '혼합형 · 일부 즉시, 일부 순차 가입';

                        return (
                            <div
                                key={opt.optionId || opt.id || idx}
                                className={`rounded-[2.5rem] p-6 border transition-all relative flex flex-col justify-between ${
                                    isExceeded
                                        ? 'bg-gray-100/90 border-gray-300 opacity-60'
                                        : `bg-white border-amber-200 ${isBest ? 'shadow-xl shadow-amber-200/50 -translate-y-2 ring-2 ring-amber-300' : 'shadow-sm'}`
                                }`}
                            >
                                {isBest && !isExceeded && (
                                    <div className="absolute -top-3.5 left-1/2 transform -translate-x-1/2 bg-amber-400 text-amber-950 font-black text-[11px] px-4 py-1 rounded-full shadow-sm border border-amber-200">
                                        👑 굴리미 강력 추천
                                    </div>
                                )}

                                <div>
                                    <div className="flex justify-between items-center mb-3">
                                        <span className={`text-xs font-bold px-3 py-1 rounded-full ${
                                            idx === 0 ? 'bg-blue-100 text-blue-800' : idx === 1 ? 'bg-amber-300 text-amber-950 font-black' : 'bg-rose-100 text-rose-800'
                                        }`}>
                                            {opt.name || (idx === 0 ? '안정 굴리미 🛡️' : idx === 1 ? '최적 굴리미 ★' : '공격 굴리미 🚀')}
                                        </span>
                                        {isExceeded && (
                                            <span className="text-[10px] font-bold bg-rose-100 text-rose-700 px-2.5 py-0.5 rounded-full">
                                                🚫 예산 초과
                                            </span>

                                    <h3 className="text-lg font-black text-amber-950 mb-1">
                                        {opt.title || (idx === 0 ? '원금 보장 꼭꼭 플랜' : idx === 1 ? 'AI 가성비 최고 플랜' : '최대 이자 도전 플랜')}
                                    </h3>
                                    <p className="text-[11px] font-extrabold text-emerald-700 mb-1">{scheduleLabel}</p>
                                    <p className="text-xs text-amber-700/60 mb-4 font-medium">
                                        최종 만기 {opt.completionMonth || userProfile?.targetMonths || 12}개월차 예상 수령액
                                    </p>

                                    {/* 예상 수령액 및 금리 비교 카드 */}
                                    <div className="bg-amber-50/50 p-4 rounded-2xl mb-4 border border-amber-100 space-y-2">
                                        <div>
                                            <span className="text-2xl font-black text-amber-950 block">
                                                {opt.totalAmount ? `${(opt.totalAmount / 10000).toLocaleString()}만원` : `${(opt.expectedAmount || 33000000 / 10000).toLocaleString()}만원`}
                                            </span>
                                        </div>

                                        <div className="pt-2 border-t border-amber-200/40 flex justify-between items-center text-xs">
                                            <span className="text-amber-700/70 line-through">
                                                광고 연 {opt.advertisedRate || opt.maxRate || '5.50'}%
                                            </span>
                                            <span className="font-extrabold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-100">
                                                실제 기대 {opt.expectedRate || '3.80'}%
                                            </span>
                                        </div>
                                    </div>

                                    <div className="space-y-2 mb-5">
                                        {(opt.allocations || []).map((allocation) => (
                                            <div key={allocation.slotIndex} className="rounded-xl border border-amber-100 bg-white p-3 text-[11px]">
                                                <div className="flex justify-between font-extrabold text-amber-950">
                                                    <span>{allocation.startMonth + 1}개월차 · {allocation.productName}</span>
                                                    <span>{(allocation.amount / 10000).toLocaleString()}만원</span>
                                                </div>
                                                <p className="mt-1 text-amber-700/70">
                                                    {allocation.bankName} · {allocation.maturityMonth}개월차 만기
                                                </p>
                                            </div>
                                        ))}
                                    </div>

                                    <div className="text-xs font-semibold text-amber-800 space-y-1.5 mb-6">
                                        <p>💳 필요 카드실적: <strong>월 {(opt.requiredCardSpend || opt.monthlyCardBudget || 0).toLocaleString()}원</strong></p>
                                        <p className="text-[11px] text-amber-700/70 font-normal">
                                            {opt.description || opt.desc || '소비 패턴에 딱 맞아 우대금리를 챙기기 제일 편해요!'}
                                        </p>
                                    </div>
                                </div>

                                <Button
                                    variant={isBest ? 'primary' : 'secondary'}
                                    onClick={() => onNext && onNext(opt)}
                                    disabled={isExceeded}
                                    className={isExceeded ? 'opacity-50 cursor-not-allowed bg-gray-200 text-gray-500 border-gray-300' : ''}
                                >
                                    {isExceeded ? '선택 불가 (예산 초과)' : '이 플랜 로드맵 보기 🚀'}
                                </Button>
                            </div>
                        );
                    })
                ) : (
                    <div className="col-span-3 text-center py-12 bg-white rounded-3xl border border-amber-200">
                        <p className="text-amber-800 font-bold text-sm">
                            {isLoading ? "🎲 백엔드 최적 파레토 플랜 산출 중..." : "플랜 데이터를 불러오는 중입니다."}
                        </p>
                    </div>
                )}
            </div>

                                        <h3 className="text-base font-black text-slate-900 mb-1">
                                            {opt.title || (idx === 0 ? '원금 보장 꼭꼭 플랜' : idx === 1 ? 'AI 가성비 최고 플랜' : '최대 이자 도전 플랜')}
                                        </h3>

                                        {/* 예상 수령액 및 금리 비교 카드 */}
                                        <div className="bg-slate-50 p-3.5 rounded-xl my-3 border border-slate-100 space-y-1.5">
                                            <div>
                                                <span className="text-[10px] text-slate-400 font-bold block">12개월 뒤 예상 수령액</span>
                                                <span className="text-xl font-black text-slate-900 block">
                                                    {opt.totalAmount ? `${(opt.totalAmount / 10000).toLocaleString()}만원` : `${((opt.expectedAmount || 33000000) / 10000).toLocaleString()}만원`}
                                                </span>
                                            </div>

                                            <div className="pt-2 border-t border-slate-200/60 flex justify-between items-center text-xs">
                                                <span className="text-slate-400 line-through text-[11px]">
                                                    광고 연 {opt.advertisedRate || opt.maxRate || '5.50'}%
                                                </span>
                                                <span className="font-extrabold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-100 text-[11px]">
                                                    실제 기대 연 {opt.expectedRate || '3.80'}%
                                                </span>
                                            </div>
                                        </div>

                                        <div className="text-xs text-slate-600 space-y-1 mb-4">
                                            <p className="font-bold text-[11px]">
                                                💳 필요 카드실적: <span className="text-slate-900 font-black">월 {(opt.requiredCardSpend || opt.monthlyCardBudget || 0).toLocaleString()}원</span>
                                            </p>
                                            <p className="text-[11px] text-slate-400 leading-snug">
                                                {opt.description || opt.desc || '소비 패턴에 딱 맞아 우대금리를 챙기기 제일 편해요!'}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="space-y-2 mb-5">
                                        {(opt.allocations || []).map((allocation) => (
                                            <div key={allocation.slotIndex} className="rounded-xl border border-amber-100 bg-white p-3 text-[11px]">
                                                <div className="flex justify-between font-extrabold text-amber-950">
                                                    <span>{allocation.startMonth + 1}개월차 · {allocation.productName}</span>
                                                    <span>{(allocation.amount / 10000).toLocaleString()}만원</span>
                                                </div>
                                                <p className="mt-1 text-amber-700/70">
                                                    {allocation.bankName} · {allocation.maturityMonth}개월차 만기
                                                </p>
                                            </div>
                                        ))}
                                    </div>

                                    <div className="text-xs font-semibold text-amber-800 space-y-1.5 mb-6">
                                        <p>💳 필요 카드실적: <strong>월 {(opt.requiredCardSpend || opt.monthlyCardBudget || 0).toLocaleString()}원</strong></p>
                                        <p className="text-[11px] text-amber-700/70 font-normal">
                                            {opt.description || opt.desc || '소비 패턴에 딱 맞아 우대금리를 챙기기 제일 편해요!'}
                                        </p>
                                    </div>
                                </div>
                            );
                        })
                    ) : (
                        <div className="bg-white rounded-2xl p-8 text-center border border-slate-200 space-y-2 my-4">
                            <span className="text-3xl block">🎲</span>
                            <p className="font-bold text-slate-700 text-xs">
                                {isLoading ? "백엔드 최적 파레토 플랜 산출 중..." : "플랜 데이터를 불러오는 중입니다."}
                            </p>
                        </div>
                    )}
                </div>
            </main>

            {/* 하단 고정 스티키 컨트롤 바 (Mobile Bottom Bar) */}
            {/* 이전/다음 버튼 */}
            {onPrev && (
                <div className="flex justify-start">
                    <button
                        onClick={onPrev}
                        className="px-6 py-3 bg-amber-100 hover:bg-amber-200 text-amber-950 font-bold rounded-2xl transition-all text-xs"
                    >
                        👈 이전 단계(프로필 수정)로 돌아가기
                    </button>
                </div>
            )}
        </div>
    );
}
