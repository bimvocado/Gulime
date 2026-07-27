import React from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';

export default function RoadmapPage() {
    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto">
            {/* 요약 상자 */}
            <div className="bg-amber-300/90 p-8 rounded-[2.5rem] shadow-xl shadow-amber-200/50 border border-amber-300 relative overflow-hidden">
                <div className="flex flex-wrap justify-between items-center gap-4">
                    <div>
            <span className="bg-white/80 text-amber-950 text-xs font-black px-3 py-1 rounded-full mb-2 inline-block">
              🎉 선택한 플랜: 균형 굴리미
            </span>
                        <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                            12개월 뒤 목표: <span className="underline decoration-amber-500">3,380만원</span>
                        </h1>
                    </div>
                    <Button variant="outline" className="text-xs py-2.5 px-4 bg-white/90">
                        📅 Google 달력에 캘린더 등록
                    </Button>
                </div>
            </div>

            {/* 타임라인 */}
            <Card title="1~12개월 맞춤 타임라인" icon="🗺️" subtitle="체크리스트대로만 따라하면 우대금리 완성!">
                <div className="relative pl-6 border-l-2 border-amber-300 space-y-8 my-4">
                    {/* 1개월차 */}
                    <div className="relative">
                        <div className="absolute -left-[31px] top-0 w-4 h-4 bg-amber-400 rounded-full border-4 border-white shadow"></div>
                        <div className="bg-amber-50/60 p-5 rounded-2xl border border-amber-100">
                            <span className="text-xs font-black bg-amber-200 text-amber-900 px-2.5 py-0.5 rounded-full">1개월 차</span>
                            <h4 className="font-extrabold text-amber-950 text-sm mt-2 mb-3">계좌 개설 및 자동이체 세팅</h4>
                            <ul className="space-y-2 text-xs font-bold text-amber-900">
                                <li className="flex items-center gap-2">
                                    <input type="checkbox" defaultChecked className="w-4 h-4 accent-amber-400 rounded-lg" />
                                    <span>KB 청년희망적금 계좌 개설</span>
                                </li>
                                <li className="flex items-center gap-2">
                                    <input type="checkbox" defaultChecked className="w-4 h-4 accent-amber-400 rounded-lg" />
                                    <span>주거래 은행 급여이체 설정 완료하기</span>
                                </li>
                            </ul>
                        </div>
                    </div>

                    {/* 2~11개월차 */}
                    <div className="relative">
                        <div className="absolute -left-[31px] top-0 w-4 h-4 bg-amber-200 rounded-full border-4 border-white"></div>
                        <div className="bg-white p-5 rounded-2xl border border-amber-100 shadow-sm">
                            <span className="text-xs font-bold bg-amber-100 text-amber-800 px-2.5 py-0.5 rounded-full">2~11개월 차</span>
                            <h4 className="font-extrabold text-amber-950 text-sm mt-2 mb-2">매월 25일 카드 실적 & 적금 체크</h4>
                            <p className="text-xs text-amber-700/70 font-medium">월 50만원 카드 실적과 적금 100만원이 자동으로 잘 들어가는지 확인해 주세요!</p>
                        </div>
                    </div>

                    {/* 12개월차 */}
                    <div className="relative">
                        <div className="absolute -left-[31px] top-0 w-4 h-4 bg-emerald-400 rounded-full border-4 border-white shadow"></div>
                        <div className="bg-emerald-50/60 p-5 rounded-2xl border border-emerald-100">
                            <span className="text-xs font-black bg-emerald-200 text-emerald-900 px-2.5 py-0.5 rounded-full">12개월 차 (만기)</span>
                            <h4 className="font-extrabold text-emerald-950 text-sm mt-2 mb-1">🎉 축하합니다! 만기 이자 수령</h4>
                            <p className="text-xs text-emerald-800/80 font-medium">목표 금액 달성 완료! 굴리미가 다음 기수 재투자 포트폴리오 알림을 보내드릴게요.</p>
                        </div>
                    </div>
                </div>
            </Card>
        </div>
    );
}