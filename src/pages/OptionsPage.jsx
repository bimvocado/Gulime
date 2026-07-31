import React, { useState, useEffect, useCallback } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function OptionsPage({ userProfile, onNext, onPrev }) {
    // ===== 기존 State 및 백엔드 연동 로직 100% 유지 =====
    const [riskTolerance, setRiskTolerance] = useState(50);
    const [optionsData, setOptionsData] = useState(null);
    const [isLoading, setIsLoading] = useState(false);

    // 백엔드 /api/v1/options 호출
    const fetchOptions = useCallback(async (riskVal) => {
        setIsLoading(true);

        const profile = userProfile || {
            employment: "FULL_TIME",
            salaryTransferable: true,
            lumpSum: 20000000,
            emergencyFund: 3000000,
            monthlySaving: 1000000,
            cardSpend6m: [250000, 300000, 200000, 280000, 320000, 220000],
            cardBudgetCap: 300000,
            existingBanks: []
        };

        const requestBody = {
            profile: profile,
            riskTolerance: riskVal / 100.0
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

    useEffect(() => {
        const timer = setTimeout(() => {
            fetchOptions(riskTolerance);
        }, 300);
        return () => clearTimeout(timer);
    }, [riskTolerance, fetchOptions]);

    return (
        /* 스마트폰 화면비 고정 (Max-Width 430px / Mobile Container) */
        <div className="max-w-[430px] mx-auto min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between shadow-2xl relative border-x border-slate-200">

            {/* 상단 모바일 앱 헤더 */}
            <header className="sticky top-0 z-30 bg-white/90 backdrop-blur-md border-b border-slate-100 px-5 py-3.5 flex items-center justify-between">
                <div className="flex items-center space-x-2">
                    <span className="text-xl">✨</span>
                    <span className="font-extrabold text-amber-950 text-lg tracking-tight">굴리미 AI</span>
                </div>
                <span className="text-[11px] font-black bg-amber-100 text-amber-800 px-2.5 py-1 rounded-full">
                    Step 2. 플랜 선택
                </span>
            </header>

            {/* 모바일 메인 스크롤 영역 */}
            <main className="flex-1 overflow-y-auto px-4 pt-4 pb-32">
                {/* 타이틀 및 설명 */}
                <div className="mb-4">
                    <h1 className="text-xl font-black text-amber-950 tracking-tight leading-snug">
                        어떤 스타일로<br />자산을 굴려볼까요?
                    </h1>
                    <p className="text-xs text-slate-500 font-medium mt-1">
                        자원 제약을 고려해 도출된 파레토 최적 플랜입니다.
                    </p>
                </div>

                {/* 🎚️ 리스크 조절 슬라이더 (모바일 카드 컴포넌트) */}
                <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm mb-5">
                    <div className="flex justify-between items-center mb-2">
                        <span className="text-xs font-black text-slate-800 flex items-center gap-1">
                            🎚️ 리스크 민감도
                        </span>
                        <span className="text-[11px] font-extrabold bg-amber-100 text-amber-900 px-2.5 py-0.5 rounded-full">
                            {riskTolerance}% {isLoading && "🔄"}
                        </span>
                    </div>

                    <input
                        type="range"
                        min="0"
                        max="100"
                        value={riskTolerance}
                        onChange={(e) => setRiskTolerance(Number(e.target.value))}
                        className="w-full accent-amber-500 h-2 bg-slate-100 rounded-lg cursor-pointer my-2"
                    />

                    <div className="flex justify-between items-center text-[10px] font-bold text-slate-400">
                        <span className="text-blue-600">🛡️ 안전 우대</span>
                        <span className="text-rose-600">🔥 수익 우대</span>
                    </div>
                </div>

                {/* 3가지 카드 선택지 (모바일 세로형 카드 스크롤) */}
                <div className="space-y-4">
                    {optionsData?.options && optionsData.options.length > 0 ? (
                        optionsData.options.map((opt, idx) => {
                            const isBest = idx === 1; // 가운데(균형형) 플랜을 대표 추천으로 표기
                            const isExceeded = opt.isBudgetExceeded || opt.exceeded || false;

                            return (
                                <div
                                    key={opt.optionId || opt.id || idx}
                                    className={`rounded-2xl p-5 border transition-all relative flex flex-col justify-between ${
                                        isExceeded
                                            ? 'bg-slate-100 border-slate-300 opacity-60'
                                            : `bg-white border-slate-200 ${
                                                isBest
                                                    ? 'ring-2 ring-amber-400 shadow-md shadow-amber-500/10'
                                                    : 'shadow-sm'
                                            }`
                                    }`}
                                >
                                    {/* 강력 추천 뱃지 */}
                                    {isBest && !isExceeded && (
                                        <div className="absolute -top-3 left-4 bg-amber-400 text-amber-950 font-black text-[10px] px-3 py-0.5 rounded-full shadow-sm border border-amber-300">
                                            👑 굴리미 강력 추천
                                        </div>
                                    )}

                                    <div>
                                        <div className="flex justify-between items-center mb-2 mt-1">
                                            <span className={`text-[11px] font-extrabold px-2.5 py-0.5 rounded-md ${
                                                idx === 0
                                                    ? 'bg-blue-50 text-blue-700 border border-blue-100'
                                                    : idx === 1
                                                        ? 'bg-amber-100 text-amber-900 border border-amber-200'
                                                        : 'bg-rose-50 text-rose-700 border border-rose-100'
                                            }`}>
                                                {opt.name || (idx === 0 ? '안정 굴리미 🛡️' : idx === 1 ? '최적 굴리미 ★' : '공격 굴리미 🚀')}
                                            </span>

                                            {isExceeded && (
                                                <span className="text-[10px] font-bold bg-rose-100 text-rose-700 px-2 py-0.5 rounded-md">
                                                    🚫 예산 초과
                                                </span>
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

                                    {/* 플랜 선택 버튼 */}
                                    <Button
                                        variant={isBest ? 'primary' : 'secondary'}
                                        onClick={() => onNext && onNext(opt)}
                                        disabled={isExceeded}
                                        className={`w-full py-3 text-xs font-black rounded-xl transition-all ${
                                            isExceeded
                                                ? 'opacity-50 cursor-not-allowed bg-slate-200 text-slate-400 border-slate-300'
                                                : isBest
                                                    ? 'bg-amber-500 hover:bg-amber-600 text-white shadow-md shadow-amber-500/20'
                                                    : 'bg-slate-100 hover:bg-slate-200 text-slate-800 border border-slate-200'
                                        }`}
                                    >
                                        {isExceeded ? '선택 불가 (예산 초과)' : '이 플랜 선택 & 로드맵 보기 🚀'}
                                    </Button>
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
            {onPrev && (
                <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] p-4 bg-white/90 backdrop-blur-md border-t border-slate-200 z-40">
                    <button
                        onClick={onPrev}
                        className="w-full py-3 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-xl transition-all text-xs border border-slate-200 active:scale-[0.98]"
                    >
                        👈 이전 단계(프로필 수정)로 돌아가기
                    </button>
                </div>
            )}
        </div>
    );
}