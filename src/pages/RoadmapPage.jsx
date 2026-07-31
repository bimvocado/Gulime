import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function RoadmapPage({ onPrev }) {
    // 🔔 리스크 고지 팝업 모달 상태 (기존 로직 100% 유지)
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isConfirmed, setIsConfirmed] = useState(false);

    const handleConfirm = () => {
        setIsConfirmed(true);
        setIsModalOpen(false);
        alert('🎉 12개월 저축 로드맵이 최종 저장되었습니다! 알림을 통해 매월 체크해 드릴게요.');
    };

    return (
        /* 스마트폰 화면비 고정 (Max-Width 430px / Mobile Container) */
        <div className="max-w-[430px] mx-auto min-h-screen bg-slate-50 text-slate-900 flex flex-col justify-between shadow-2xl relative border-x border-slate-200">

            {/* 상단 모바일 앱 헤더 */}
            <header className="sticky top-0 z-30 bg-white/90 backdrop-blur-md border-b border-slate-100 px-5 py-3.5 flex items-center justify-between">
                <div className="flex items-center space-x-2">
                    <span className="text-xl">🗺️</span>
                    <span className="font-extrabold text-amber-950 text-lg tracking-tight">굴리미 AI</span>
                </div>
                <span className="text-[11px] font-black bg-amber-100 text-amber-800 px-2.5 py-1 rounded-full">
                    Step 3. 로드맵
                </span>
            </header>

            {/* 모바일 메인 스크롤 영역 */}
            <main className="flex-1 overflow-y-auto px-4 pt-4 pb-32">
                {/* 메인 목표 카드 */}
                <div className="bg-amber-400 p-5 rounded-2xl shadow-md shadow-amber-500/10 border border-amber-300 relative overflow-hidden mb-5">
                    <span className="bg-white/90 text-amber-950 text-[10px] font-black px-2.5 py-0.5 rounded-full mb-1.5 inline-block">
                        🎉 선택한 플랜: 균형 굴리미
                    </span>
                    <h1 className="text-xl font-black text-amber-950 tracking-tight leading-snug">
                        12개월 뒤 목표<br />
                        <span className="text-2xl text-slate-900 underline decoration-amber-600">3,380만원</span>
                    </h1>

                    <div className="mt-3 pt-3 border-t border-amber-500/30 flex justify-between items-center">
                        <span className="text-[11px] font-bold text-amber-950/80">달력 연동 알림</span>
                        <button className="text-[11px] font-black px-3 py-1.5 bg-white text-amber-950 rounded-xl shadow-sm active:scale-95 transition-transform">
                            📅 Google 달력 등록
                        </button>
                    </div>
                </div>

                {/* 타임라인 카드 */}
                <Card title="1~12개월 맞춤 타임라인" icon="🗺️" subtitle="체크리스트대로만 따라하면 우대금리 완성!">
                    <div className="relative pl-5 border-l-2 border-amber-300 space-y-6 my-2 text-xs">

                        {/* 1개월차 */}
                        <div className="relative">
                            <div className="absolute -left-[27px] top-0.5 w-3.5 h-3.5 bg-amber-500 rounded-full border-2 border-white shadow-sm"></div>
                            <div className="bg-amber-50/80 p-3.5 rounded-xl border border-amber-200">
                                <span className="text-[10px] font-black bg-amber-200 text-amber-900 px-2 py-0.5 rounded-md">1개월 차</span>
                                <h4 className="font-black text-amber-950 text-xs mt-1.5 mb-2">계좌 개설 및 자동이체 세팅</h4>
                                <ul className="space-y-1.5 font-bold text-amber-900 text-[11px]">
                                    <li className="flex items-center gap-1.5">
                                        <input type="checkbox" defaultChecked className="w-3.5 h-3.5 accent-amber-500 rounded" />
                                        <span>KB 청년희망적금 계좌 개설</span>
                                    </li>
                                    <li className="flex items-center gap-1.5">
                                        <input type="checkbox" defaultChecked className="w-3.5 h-3.5 accent-amber-500 rounded" />
                                        <span>주거래 은행 급여이체 설정 완료</span>
                                    </li>
                                </ul>
                            </div>
                        </div>

                        {/* 2~11개월차 */}
                        <div className="relative">
                            <div className="absolute -left-[27px] top-0.5 w-3.5 h-3.5 bg-amber-200 rounded-full border-2 border-white"></div>
                            <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm">
                                <span className="text-[10px] font-bold bg-amber-100 text-amber-800 px-2 py-0.5 rounded-md">2~11개월 차</span>
                                <h4 className="font-black text-slate-900 text-xs mt-1.5 mb-1">매월 25일 카드 실적 & 적금 체크</h4>
                                <p className="text-[11px] text-slate-500 leading-tight">
                                    월 50만원 카드 실적과 적금 100만원이 자동으로 잘 들어가는지 확인해 주세요!
                                </p>
                            </div>
                        </div>

                        {/* 12개월차 */}
                        <div className="relative">
                            <div className="absolute -left-[27px] top-0.5 w-3.5 h-3.5 bg-emerald-500 rounded-full border-2 border-white shadow-sm"></div>
                            <div className="bg-emerald-50/80 p-3.5 rounded-xl border border-emerald-200">
                                <span className="text-[10px] font-black bg-emerald-200 text-emerald-900 px-2 py-0.5 rounded-md">12개월 차 (만기)</span>
                                <h4 className="font-black text-emerald-950 text-xs mt-1.5 mb-1">🎉 축하합니다! 만기 이자 수령</h4>
                                <p className="text-[11px] text-emerald-800 leading-tight">
                                    목표 금액 달성 완료! 굴리미가 다음 기수 재투자 포트폴리오 알림을 보내드릴게요.
                                </p>
                            </div>
                        </div>

                    </div>
                </Card>
            </main>

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