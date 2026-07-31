import React, { useEffect, useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function RoadmapPage({ userProfile, selectedOption, onPrev }) {
    // 🔔 리스크 고지 팝업 모달 상태
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isConfirmed, setIsConfirmed] = useState(false);
    const [roadmap, setRoadmap] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!userProfile || !selectedOption?.allocations) return;

        fetch('/api/v1/roadmap', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                profile: userProfile,
                selectedAllocations: selectedOption.allocations.map((item) => ({
                    slotIndex: item.slotIndex,
                    productId: item.productId,
                    amount: item.amount,
                    startMonth: item.startMonth
                }))
            })
        })
            .then(async (response) => {
                if (!response.ok) throw new Error(await response.text());
                return response.json();
            })
            .then(setRoadmap)
            .catch((requestError) => setError(requestError.message));
    }, [userProfile, selectedOption]);

    const handleConfirm = () => {
        setIsConfirmed(true);
        setIsModalOpen(false);
        alert('🎉 12개월 저축 로드맵이 최종 저장되었습니다! 알림을 통해 매월 체크해 드릴게요.');
    };

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto pb-12">
            {/* 요약 상자 */}
            <div className="bg-amber-300/90 p-8 rounded-[2.5rem] shadow-xl shadow-amber-200/50 border border-amber-300 relative overflow-hidden">
                <div className="flex flex-wrap justify-between items-center gap-4">
                    <div>
                        <span className="bg-white/80 text-amber-950 text-xs font-black px-3 py-1 rounded-full mb-2 inline-block">
                          🎉 선택한 플랜: {selectedOption?.optionType || '굴리미'}
                        </span>
                        <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                            풍차 운용 목표: <span className="underline decoration-amber-500">
                                {((selectedOption?.expectedFinalAmount || 0) / 10000).toLocaleString()}만원
                            </span>
                        </h1>
                    </div>
                </div>

            <Card title="풍차 가입·만기 타임라인" icon="🗺️" subtitle="가입월을 한 달씩 엇갈려 만기도 순차적으로 돌아옵니다.">
                <div className="relative pl-6 border-l-2 border-amber-300 space-y-8 my-4">
                    {error && <p className="text-xs font-bold text-rose-700">로드맵 조회 실패: {error}</p>}
                    {!roadmap && !error && <p className="text-xs font-bold text-amber-700">로드맵 계산 중...</p>}
                    {(roadmap?.milestones || [])
                        .filter((item) => item.eventType !== 'START')
                        .map((item, index) => (
                            <div className="relative" key={`${item.month}-${item.eventType}-${item.productId || index}`}>
                                <div className={`absolute -left-[31px] top-0 w-4 h-4 rounded-full border-4 border-white shadow ${item.eventType.includes('MATURITY') ? 'bg-emerald-400' : 'bg-amber-400'}`}></div>
                                <div className="bg-white p-5 rounded-2xl border border-amber-100 shadow-sm">
                                    <span className="text-xs font-black bg-amber-100 text-amber-900 px-2.5 py-0.5 rounded-full">
                                        {item.month === 0 ? 1 : item.month}개월 차 · {item.eventType}
                                    </span>
                                    <h4 className="font-extrabold text-amber-950 text-sm mt-2">{item.productName || '대기 자금'}</h4>
                                    <p className="mt-1 text-xs text-amber-700/70">{item.action}</p>
                                    <p className="mt-2 text-xs font-black text-amber-900">{item.amount.toLocaleString()}원</p>
                                </div>
                            </div>
                        ))}
                </div>
            </Card>

            {/* 하단 고정 액션 컨트롤 바 (Mobile Bottom Sticky Bar) */}
            <div className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] p-4 bg-white/90 backdrop-blur-md border-t border-slate-200 z-40 flex gap-2">
                {onPrev && (
                    <button
                        onClick={onPrev}
                        className="w-1/3 py-3 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-xl transition-all text-xs border border-slate-200 active:scale-[0.98]"
                    >
                        👈 다시 고르기
                    </button>
                )}
                <Button
                    onClick={() => setIsModalOpen(true)}
                    className={`py-3 text-xs font-black rounded-xl transition-all shadow-md active:scale-[0.98] ${
                        onPrev ? 'w-2/3' : 'w-full'
                    } ${
                        isConfirmed
                            ? 'bg-emerald-500 hover:bg-emerald-600 text-white shadow-emerald-500/20'
                            : 'bg-amber-500 hover:bg-amber-600 text-white shadow-amber-500/20'
                    }`}
                >
                    {isConfirmed ? '✅ 로드맵 확정 완료됨' : '🚀 최종 확정하기'}
                </Button>
            </div>

            {/* ⚠️ 리스크 고지 팝업 모달 (모바일 맞춤) */}
            {isModalOpen && (
                <div className="fixed inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4 animate-fadeIn">
                    <div className="bg-white rounded-2xl p-5 max-w-[360px] w-full border border-slate-200 shadow-2xl space-y-4">
                        <div className="text-center space-y-1">
                            <span className="text-3xl block mb-1">⚠️</span>
                            <h3 className="text-base font-black text-slate-900">최종 확정 전 확인해 주세요</h3>
                            <p className="text-[10px] text-amber-800 font-bold bg-amber-50 px-2 py-0.5 rounded-full inline-block">
                                우대금리 이행 리스크 고지서
                            </p>
                        </div>

                        <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 text-[11px] text-slate-600 space-y-1.5 font-medium leading-tight">
                            <p>• 입력하신 소비 패턴 기준 <strong>기대금리(E[r])</strong>입니다.</p>
                            <p>• 실적 미달(카드 사용량 부족, 급여이체 중단 등) 시 <strong>우대금리가 적용되지 않아 최종 만기 이자가 변동</strong>될 수 있습니다.</p>
                            <p>• 중도 해지 시 약정 금리가 아닌 중도해지 금리가 적용됩니다.</p>
                        </div>

                        <div className="flex gap-2 pt-1">
                            <button
                                onClick={() => setIsModalOpen(false)}
                                className="w-1/3 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-xl text-xs"
                            >
                                취소
                            </button>
                            <Button onClick={handleConfirm} className="w-2/3 py-2.5 text-xs bg-amber-500 text-white font-black rounded-xl">
                                동의하고 확정하기 ✨
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
