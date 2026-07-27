import React from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function OptionsPage({ onNext }) {
    const options = [
        {
            id: 'stable',
            type: '안정 굴리미 🛡️',
            title: '원금 보장 꼭꼭 플랜',
            amount: '3,245만원',
            rate: '+2.45%',
            card: '월 30만원 (여유)',
            badge: 'bg-blue-100 text-blue-800',
            border: 'border-amber-100'
        },
        {
            id: 'balanced',
            type: '최적 굴리미 ★',
            title: 'AI 가성비 최고 플랜',
            amount: '3,380만원',
            rate: '+3.80%',
            card: '월 50만원 (딱 맞음)',
            badge: 'bg-amber-300 text-amber-950 font-black',
            border: 'border-amber-300 ring-2 ring-amber-200/80',
            isBest: true
        },
        {
            id: 'aggressive',
            type: '공격 굴리미 🚀',
            title: '최대 이자 도전 플랜',
            amount: '3,450만원',
            rate: '+4.50%',
            card: '월 65만원 (주의)',
            badge: 'bg-rose-100 text-rose-800',
            border: 'border-amber-100'
        }
    ];

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto">
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

            {/* 3가지 카드 선택지 */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {options.map((opt) => (
                    <div key={opt.id} className={`bg-white rounded-[2.5rem] p-6 border transition-all relative flex flex-col justify-between ${opt.border} ${opt.isBest ? 'shadow-xl shadow-amber-200/50 -translate-y-2' : 'shadow-sm'}`}>
                        {opt.isBest && (
                            <div className="absolute -top-3.5 left-1/2 transform -translate-x-1/2 bg-amber-400 text-amber-950 font-black text-[11px] px-4 py-1 rounded-full shadow-sm border border-amber-200">
                                👑 굴리미 강력 추천
                            </div>
                        )}

                        <div>
                            <div className="flex justify-between items-center mb-3">
                                <span className={`text-xs font-bold px-3 py-1 rounded-full ${opt.badge}`}>{opt.type}</span>
                            </div>
                            <h3 className="text-lg font-black text-amber-950 mb-1">{opt.title}</h3>
                            <p className="text-xs text-amber-700/60 mb-6 font-medium">12개월 뒤 예상 금액</p>

                            <div className="bg-amber-50/50 p-4 rounded-2xl mb-4 border border-amber-100">
                                <span className="text-2xl font-black text-amber-950 block">{opt.amount}</span>
                                <span className="text-xs font-bold text-emerald-600">{opt.rate} 기대수익</span>
                            </div>

                            <div className="text-xs font-semibold text-amber-800 space-y-1 mb-6">
                                <p>💳 필요 카드실적: <strong>{opt.card}</strong></p>
                            </div>
                        </div>

                        <Button
                            variant={opt.isBest ? 'primary' : 'secondary'}
                            onClick={onNext}
                        >
                            이 플랜 선택하기
                        </Button>
                    </div>
                ))}
            </div>

            {/* 파레토 그래프 가이드 */}
            <Card title="수익 vs 리스크 차트" icon="📊" subtitle="위쪽에 있을수록 수익이 높고, 왼쪽에 있을수록 안전해요">
                <div className="h-48 bg-amber-50/40 rounded-2xl border border-dashed border-amber-200 flex items-center justify-around relative p-4">
                    <div className="flex flex-col items-center gap-1 cursor-pointer">
                        <div className="w-8 h-8 bg-blue-200 rounded-full flex items-center justify-center font-black text-xs text-blue-900 shadow-sm">🛡️</div>
                        <span className="text-xs font-bold text-amber-900">안정형</span>
                    </div>
                    <div className="flex flex-col items-center gap-1 cursor-pointer transform -translate-y-6">
                        <div className="w-10 h-10 bg-amber-300 rounded-full flex items-center justify-center font-black text-sm text-amber-950 shadow-md ring-4 ring-white">👑</div>
                        <span className="text-xs font-black text-amber-950">균형형 (추천)</span>
                    </div>
                    <div className="flex flex-col items-center gap-1 cursor-pointer transform -translate-y-10">
                        <div className="w-8 h-8 bg-rose-200 rounded-full flex items-center justify-center font-black text-xs text-rose-900 shadow-sm">🚀</div>
                        <span className="text-xs font-bold text-amber-900">공격형</span>
                    </div>
                </div>
            </Card>
        </div>
    );
}