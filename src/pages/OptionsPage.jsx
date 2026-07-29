import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function OptionsPage({ onNext, onPrev }) {
    // 1. 리스크 슬라이더 상태 (0: 안전 ~ 100: 최대 수익)
    const [riskTolerance, setRiskTolerance] = useState(50);

    // 2. 파레토 3가지 선택지 데이터
    // (예산 초과인 옵션은 isExceeded: true 처리)
    const options = [
        {
            id: 'stable',
            type: '안정 굴리미 🛡️',
            title: '원금 보장 꼭꼭 플랜',
            amount: '3,245만원',
            advertisedRate: '연 3.20%', // 광고 최고 금리
            expectedRate: '2.45%',     // 실질 기대 금리
            card: '월 30만원 (여유)',
            badge: 'bg-blue-100 text-blue-800',
            border: 'border-amber-100',
            isExceeded: false,
            desc: '카드 실적 부담이 적고 달성 확률 95% 이상!'
        },
        {
            id: 'balanced',
            type: '최적 굴리미 ★',
            title: 'AI 가성비 최고 플랜',
            amount: '3,380만원',
            advertisedRate: '연 5.50%',
            expectedRate: '3.80%',
            card: '월 50만원 (딱 맞음)',
            badge: 'bg-amber-300 text-amber-950 font-black',
            border: 'border-amber-300 ring-2 ring-amber-200/80',
            isBest: true,
            isExceeded: false,
            desc: '소비 패턴에 딱 맞아 우대금리를 챙기기 제일 편해요!'
        },
        {
            id: 'aggressive',
            type: '공격 굴리미 🚀',
            title: '최대 이자 도전 플랜',
            amount: '3,450만원',
            advertisedRate: '연 7.00%',
            expectedRate: '4.50%',
            card: '월 80만원 (예산 초과)',
            badge: 'bg-rose-100 text-rose-800',
            border: 'border-amber-100',
            isExceeded: true, // ⚠️ 유저 예산 상한 초과시 회색 처리 스펙!
            desc: '월 카드실적 80만원 요구 (현재 예산 상한 초과!)'
        }
    ];

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto">
            {/* 헤더 */}
            <div className="text-center space-y-2 py-4">
                <span className="bg-amber-200/70 text-amber-900 text-xs font-black px-3 py-1 rounded-full border border-amber-300/50">
                  ✨ Step 2. 굴리미 추천 플랜 선택
                </span>
                <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                    어떤 스타일로 굴려볼까요?
                </h1>
                <p className="text-amber-800/60 text-xs font-semibold">
                    자원 제약을 꼼꼼히 계산해 도출한 3가지 최선안입니다.
                </p>
            </div>

            {/* 🎚️ 리스크 조절 슬라이더 */}
            <Card title="내 리스크 민감도 조절" icon="🎚️" subtitle="슬라이더를 움직여 달성 확률과 목표 수익 사이의 균형을 맞추세요">
                <div className="space-y-3 py-1">
                    <div className="flex justify-between items-center text-xs font-extrabold text-amber-950">
                        <span className="text-blue-700">🛡️ 안전 우대 (확률 위주)</span>
                        <span className="bg-amber-200/80 px-3 py-1 rounded-full text-amber-900">
                            위험 선호도: {riskTolerance}%
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

            {/* 3가지 카드 선택지 (예산 초과 시 회색 및 클릭 불가 처리) */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {options.map((opt) => (
                    <div
                        key={opt.id}
                        className={`rounded-[2.5rem] p-6 border transition-all relative flex flex-col justify-between ${
                            opt.isExceeded
                                ? 'bg-gray-100/90 border-gray-300 opacity-60' // ⚠️ 예산 초과 시 회색 처리!
                                : `bg-white ${opt.border} ${opt.isBest ? 'shadow-xl shadow-amber-200/50 -translate-y-2' : 'shadow-sm'}`
                        }`}
                    >
                        {opt.isBest && !opt.isExceeded && (
                            <div className="absolute -top-3.5 left-1/2 transform -translate-x-1/2 bg-amber-400 text-amber-950 font-black text-[11px] px-4 py-1 rounded-full shadow-sm border border-amber-200">
                                👑 굴리미 강력 추천
                            </div>
                        )}

                        <div>
                            <div className="flex justify-between items-center mb-3">
                                <span className={`text-xs font-bold px-3 py-1 rounded-full ${opt.isExceeded ? 'bg-gray-200 text-gray-600' : opt.badge}`}>
                                    {opt.type}
                                </span>
                                {opt.isExceeded && (
                                    <span className="text-[10px] font-bold bg-rose-100 text-rose-700 px-2.5 py-0.5 rounded-full">
                                        🚫 예산 초과
                                    </span>
                                )}
                            </div>

                            <h3 className="text-lg font-black text-amber-950 mb-1">{opt.title}</h3>
                            <p className="text-xs text-amber-700/60 mb-4 font-medium">12개월 뒤 예상 금액</p>

                            {/* 예상 수령액 및 금리 비교 카드 */}
                            <div className="bg-amber-50/50 p-4 rounded-2xl mb-4 border border-amber-100 space-y-2">
                                <div>
                                    <span className="text-2xl font-black text-amber-950 block">{opt.amount}</span>
                                </div>

                                {/* 🎯 핵심: 광고금리 vs 기대금리 비교 뷰 */}
                                <div className="pt-2 border-t border-amber-200/40 flex justify-between items-center text-xs">
                                    <span className="text-amber-700/70 line-through">광고 {opt.advertisedRate}</span>
                                    <span className="font-extrabold text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-100">
                                        실제 기대 {opt.expectedRate}
                                    </span>
                                </div>
                            </div>

                            <div className="text-xs font-semibold text-amber-800 space-y-1.5 mb-6">
                                <p>💳 필요 카드실적: <strong>{opt.card}</strong></p>
                                <p className="text-[11px] text-amber-700/70 font-normal">{opt.desc}</p>
                            </div>
                        </div>

                        <Button
                            variant={opt.isBest ? 'primary' : 'secondary'}
                            onClick={onNext}
                            disabled={opt.isExceeded}
                            className={opt.isExceeded ? 'opacity-50 cursor-not-allowed bg-gray-200 text-gray-500 border-gray-300' : ''}
                        >
                            {opt.isExceeded ? '선택 불가 (예산 초과)' : '이 플랜 선택하기'}
                        </Button>
                    </div>
                ))}
            </div>

            {/* 기존 파레토 그래프 차트 (디자인 완벽 보존) */}
            <Card title="수익 vs 리스크 파레토 차트" icon="📊" subtitle="위쪽에 있을수록 수익이 높고, 왼쪽에 있을수록 안전해요">
                <div className="h-48 bg-amber-50/40 rounded-2xl border border-dashed border-amber-200 flex items-center justify-around relative p-4">
                    <div className="flex flex-col items-center gap-1 cursor-pointer hover:scale-110 transition-transform">
                        <div className="w-8 h-8 bg-blue-200 rounded-full flex items-center justify-center font-black text-xs text-blue-900 shadow-sm">🛡️</div>
                        <span className="text-xs font-bold text-amber-900">안정형</span>
                    </div>
                    <div className="flex flex-col items-center gap-1 cursor-pointer transform -translate-y-6 hover:scale-110 transition-transform">
                        <div className="w-10 h-10 bg-amber-300 rounded-full flex items-center justify-center font-black text-sm text-amber-950 shadow-md ring-4 ring-white">👑</div>
                        <span className="text-xs font-black text-amber-950">균형형 (추천)</span>
                    </div>
                    <div className="flex flex-col items-center gap-1 cursor-pointer transform -translate-y-10 opacity-50">
                        <div className="w-8 h-8 bg-gray-300 rounded-full flex items-center justify-center font-black text-xs text-gray-600 shadow-sm">🚀</div>
                        <span className="text-xs font-bold text-gray-500">공격형 (초과)</span>
                    </div>
                </div>
            </Card>

            {/* 이전/다음 버튼 */}
            {onPrev && (
                <div className="flex justify-start">
                    <button
                        onClick={onPrev}
                        className="px-6 py-3 bg-amber-100 hover:bg-amber-200 text-amber-950 font-bold rounded-2xl transition-all text-xs"
                    >
                        👈 이전으로 돌아가기
                    </button>
                </div>
            )}
        </div>
    );
}