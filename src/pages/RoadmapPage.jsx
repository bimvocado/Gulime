import React, { useEffect, useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import { authStorage } from '../utils/storage';

const getEventTypeInfo = (eventType) => {
    switch (eventType) {
        case 'SAVING_START':
        case 'START':
            return { label: '상품 가입 (시작)', color: 'bg-amber-100 text-amber-900 border-amber-300', dot: 'bg-amber-400' };
        case 'SAVING_MATURITY':
            return { label: '적금 만기 (원금+이자 수령)', color: 'bg-emerald-100 text-emerald-900 border-emerald-300', dot: 'bg-emerald-500' };
        case 'MATURITY':
            return { label: '정기예금 만기 (원금+이자 수령)', color: 'bg-emerald-100 text-emerald-900 border-emerald-300', dot: 'bg-emerald-500' };
        case 'REINVESTMENT':
            return { label: '만기 이자 풍차 재투자', color: 'bg-blue-100 text-blue-900 border-blue-300', dot: 'bg-blue-500' };
        default:
            return { label: eventType || '금융 이벤트', color: 'bg-gray-100 text-gray-800 border-gray-300', dot: 'bg-gray-400' };
    }
};

export default function RoadmapPage({ userProfile, selectedOption, onPrev, onConfirmSuccess }) {
    const [isModalOpen, setIsModalOpen] = useState(false);

    // 💡 [수정 1] 초기화 시 로컬 스토리지/저장소에 로드맵이 이미 존재하는지 확인하여 확정 상태 유지
    const [isConfirmed, setIsConfirmed] = useState(() => {
        try {
            const savedRoadmap = authStorage.getRoadmap();
            return !!savedRoadmap;
        } catch (e) {
            return false;
        }
    });

    const [roadmap, setRoadmap] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!userProfile || !selectedOption?.allocations) {
            setIsLoading(false);
            return;
        }

        setIsLoading(true);
        setError(null);

        const selectedAllocations = selectedOption.allocations.map((item) => ({
            slotIndex: item.slotIndex,
            productId: item.productId,
            amount: item.amount || 0,
            monthlyAmount: item.monthlyAmount || 0,
            startMonth: item.startMonth || 0
        }));

        fetch('/api/v1/roadmap', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Cache-Control': 'no-cache'
            },
            body: JSON.stringify({
                profile: userProfile,
                selectedAllocations: selectedAllocations
            })
        })
            .then(async (response) => {
                if (!response.ok) throw new Error(await response.text());
                return response.json();
            })
            .then((data) => {
                setRoadmap(data);
            })
            .catch((requestError) => {
                console.error('Roadmap API 에러:', requestError);
                setError(requestError.message);
            })
            .finally(() => {
                setIsLoading(false);
            });
    }, [userProfile, selectedOption]);

    if (!selectedOption) {
        return (
            <div className="max-w-2xl mx-auto py-16 text-center space-y-6">
                <Card title="추천 플랜 선택 필요" icon="⚠️">
                    <div className="py-10 space-y-4">
                        <p className="text-lg font-black text-amber-950">
                            선택된 추천 플랜이 없거나 최신 프로필로 갱신되었습니다.
                        </p>
                        <p className="text-xs text-amber-800/70 font-semibold">
                            Step 2에서 원하시는 굴리기 플랜을 먼저 선택해주세요.
                        </p>
                        <div className="pt-2">
                            <Button
                                onClick={onPrev}
                                className="px-8 py-3.5 bg-amber-500 text-white font-black text-sm rounded-2xl hover:bg-amber-600 shadow-md"
                            >
                                👈 플랜 선택하러 가기
                            </Button>
                        </div>
                    </div>
                </Card>
            </div>
        );
    }

    const handleConfirm = () => {
        if (!selectedOption || !roadmap) {
            alert('⚠️ 선택된 추천 플랜 또는 로드맵 정보가 정상적으로 불러와지지 않았습니다. 플랜을 다시 선택해주세요!');
            setIsModalOpen(false);
            return;
        }

        setIsConfirmed(true);
        setIsModalOpen(false);
        authStorage.setRoadmap(roadmap);

        alert('🎉 12개월 저축 로드맵이 마이페이지에 저장되었습니다!');
        if (onConfirmSuccess) onConfirmSuccess();
    };

    // 💡 [수정 2] 메인 확정/변경 버튼 클릭 핸들러
    const handleMainButtonClick = () => {
        if (isConfirmed) {
            // 이미 확정된 상태라면 -> 옵션 페이지로 돌아가서 다른 플랜 선택
            if (onPrev) onPrev();
        } else {
            // 아직 확정 안 된 상태라면 -> 확인 모달 띄우기
            setIsModalOpen(true);
        }
    };

    const summary = roadmap?.summary || {
        totalPrincipal: selectedOption?.expectedFinalAmount - selectedOption?.expectedTotalReturn || 0,
        emergencyFund: userProfile?.emergencyFund || 0,
        expectedTotalReturn: selectedOption?.expectedTotalReturn || 0,
        effectiveRate: selectedOption?.weightedExpectedRate || 0
    };

    const rawMilestones = roadmap?.milestones || [];

    const groupedMilestones = React.useMemo(() => {
        const filtered = rawMilestones.filter((item) => item.eventType !== 'START');
        const sorted = [...filtered].sort((a, b) => (a.month || 0) - (b.month || 0));

        const groups = {};
        sorted.forEach((item) => {
            const m = item.month ?? 0;
            if (!groups[m]) groups[m] = [];
            groups[m].push(item);
        });

        return groups;
    }, [rawMilestones]);

    return (
        <div className="space-y-8 animate-fadeIn max-w-5xl mx-auto pb-12">
            <div className="bg-amber-300/90 p-8 rounded-[2.5rem] shadow-xl shadow-amber-200/50 border border-amber-300 relative overflow-hidden">
                <div className="flex flex-wrap justify-between items-center gap-4">
                    <div>
                        <span className="bg-white/80 text-amber-950 text-xs font-black px-3 py-1 rounded-full mb-2 inline-block">
                          🎉 선택한 플랜: {selectedOption?.optionType || '굴리미 추천'}
                        </span>
                        <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                            풍차 운용 목표: <span className="underline decoration-amber-500">
                                {Math.floor((selectedOption?.expectedFinalAmount || 0) / 10000).toLocaleString()}만원
                            </span>
                        </h1>
                    </div>
                    <Button variant="outline" className="text-xs py-2.5 px-4 bg-white/90 shadow-sm hover:bg-white">
                        📅 Google 달력에 캘린더 등록
                    </Button>
                </div>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="bg-white p-5 rounded-2xl border border-amber-200/60 shadow-sm text-center">
                    <span className="text-[11px] font-bold text-amber-800/60">총 원금</span>
                    <p className="text-lg font-black text-amber-950 mt-1">
                        {Math.floor((summary.totalPrincipal || 0) / 10000).toLocaleString()}만원
                    </p>
                </div>
                <div className="bg-white p-5 rounded-2xl border border-amber-200/60 shadow-sm text-center">
                    <span className="text-[11px] font-bold text-amber-800/60">비상금 (파킹)</span>
                    <p className="text-lg font-black text-amber-950 mt-1">
                        {Math.floor((summary.emergencyFund || 0) / 10000).toLocaleString()}만원
                    </p>
                </div>
                <div className="bg-white p-5 rounded-2xl border border-amber-200/60 shadow-sm text-center">
                    <span className="text-[11px] font-bold text-amber-800/60">예상 세전 이자</span>
                    <p className="text-lg font-black text-emerald-600 mt-1">
                        +{Math.floor((summary.expectedTotalReturn || 0) / 10000).toLocaleString()}만원
                    </p>
                </div>
                <div className="bg-white p-5 rounded-2xl border border-amber-200/60 shadow-sm text-center">
                    <span className="text-[11px] font-bold text-amber-800/60">실질 기대 수익률</span>
                    <p className="text-lg font-black text-blue-600 mt-1">
                        {(summary.effectiveRate || summary.weightedExpectedRate || 0).toFixed(2)}%
                    </p>
                </div>
            </div>

            <Card title="풍차 가입·만기 타임라인" icon="🗺️" subtitle="가입월을 한 달씩 엇갈려 만기도 순차적으로 돌아옵니다.">
                <div className="relative pl-6 border-l-2 border-amber-300 space-y-10 my-6">
                    {error && (
                        <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl">
                            <p className="text-xs font-bold text-rose-700">로드맵 조회 실패: {error}</p>
                        </div>
                    )}

                    {isLoading && (
                        <div className="py-8 text-center text-amber-800 font-bold text-xs animate-pulse">
                            🎲 백엔드 최적 재투입 알고리즘으로 타임라인을 계산 중입니다...
                        </div>
                    )}

                    {!isLoading && Object.keys(groupedMilestones).length === 0 && !error && (
                        <div className="py-8 text-center text-amber-700 font-semibold text-xs">
                            💡 산출된 타임라인 마일스톤 정보가 없습니다.
                        </div>
                    )}

                    {!isLoading && Object.entries(groupedMilestones).map(([monthStr, items]) => {
                        const monthNum = Number(monthStr);
                        const displayMonth = monthNum === 0 ? '1개월 차 (현재)' : `${monthNum}개월 차`;

                        return (
                            <div key={monthStr} className="relative space-y-3">
                                <div className="absolute -left-[35px] top-1 flex items-center justify-center w-6 h-6 rounded-full bg-amber-400 text-amber-950 text-[10px] font-black border-2 border-white shadow-sm">
                                    {monthNum === 0 ? 1 : monthNum}
                                </div>

                                <div className="flex items-center gap-2 mb-2">
                                    <h3 className="text-sm font-black text-amber-950">
                                        📌 {displayMonth}
                                    </h3>
                                    <span className="text-[11px] text-amber-700 font-bold bg-amber-100/60 px-2 py-0.5 rounded-md">
                                        이벤트 {items.length}건
                                    </span>
                                </div>

                                <div className="space-y-3">
                                    {items.map((item, index) => {
                                        const typeInfo = getEventTypeInfo(item.eventType);
                                        const bankName = item.bankName || item.bank;

                                        return (
                                            <div
                                                key={`${item.month}-${item.eventType}-${item.productId || index}`}
                                                className="bg-white p-5 rounded-2xl border border-amber-200/80 shadow-xs hover:shadow-md transition-all"
                                            >
                                                <div className="flex justify-between items-center">
                                                    <span className={`text-[11px] font-extrabold px-3 py-1 rounded-full border ${typeInfo.color}`}>
                                                        {typeInfo.label}
                                                    </span>
                                                    <span className="text-sm font-black text-amber-950">
                                                        {(item.amount || 0).toLocaleString()}원
                                                    </span>
                                                </div>

                                                <div className="mt-2.5 flex items-center gap-2">
                                                    {bankName && (
                                                        <span className="text-[10px] font-black bg-amber-100 text-amber-900 px-2 py-0.5 rounded-md border border-amber-200/70 shrink-0">
                                                            {bankName}
                                                        </span>
                                                    )}
                                                    <h4 className="font-extrabold text-amber-950 text-base">
                                                        {item.productName || '대기 자금 (파킹통장)'}
                                                    </h4>
                                                </div>

                                                {item.action && (
                                                    <p className="mt-2 text-xs text-amber-800/80 font-medium bg-amber-50/50 p-2.5 rounded-xl border border-amber-100">
                                                        💡 {item.action}
                                                    </p>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </Card>

            <div className="flex gap-4 pt-2">
                {onPrev && (
                    <button
                        onClick={onPrev}
                        className="w-1/3 py-4 bg-amber-100 hover:bg-amber-200 text-amber-900 font-black rounded-2xl transition-all text-sm"
                    >
                        👈 플랜 다시 고르기
                    </button>
                )}

                {/* 💡 [수정 3] 확정 상태에 따른 버튼 문구, 디자인, 동작 전환 */}
                <Button
                    onClick={handleMainButtonClick}
                    className={`text-base py-4 ${onPrev ? 'w-2/3' : 'w-full'} ${
                        isConfirmed
                            ? 'bg-emerald-600 hover:bg-emerald-700 text-white shadow-md'
                            : 'bg-amber-500 hover:bg-amber-600 text-white'
                    }`}
                >
                    {isConfirmed ? '✅ 로드맵 확정 완료 (다른 플랜 고르기 🔄)' : '🚀 이 로드맵으로 최종 확정하기'}
                </Button>
            </div>

            {isModalOpen && (
                <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fadeIn">
                    <div className="bg-white rounded-[2.5rem] p-8 max-w-lg w-full border-2 border-amber-300 shadow-2xl space-y-6">
                        <div className="text-center space-y-2">
                            <span className="text-4xl">⚠️</span>
                            <h3 className="text-xl font-black text-amber-950">최종 확정 전 꼭 확인해 주세요!</h3>
                            <p className="text-xs text-amber-800/70 font-semibold">굴리미 우대금리 이행 리스크 고지서</p>
                        </div>

                        {roadmap?.risks?.length > 0 && (
                            <div className="bg-rose-50 p-4 rounded-2xl border border-rose-200 space-y-2">
                                <span className="text-xs font-black text-rose-900 block">🚨 우대조건 미달성 위험 진단:</span>
                                <ul className="space-y-1 text-xs text-rose-800 font-medium">
                                    {roadmap.risks.map((risk, rIdx) => (
                                        <li key={rIdx} className="flex items-start gap-1">
                                            <span>•</span>
                                            <span>{risk.message}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}

                        <div className="bg-amber-50 p-4 rounded-2xl border border-amber-200 text-xs text-amber-900 space-y-2 font-medium leading-relaxed">
                            <p>• 본 시뮬레이션 결과는 입력하신 소비 패턴과 몬테카를로 진단을 바탕으로 산출된 <strong>기대금리(E[r])</strong>입니다.</p>
                            <p>• 실적 미달(카드 사용량 부족, 급여이체 중단 등) 시 <strong>약정된 우대금리가 적용되지 않아 최종 만기 이자가 변동</strong>될 수 있습니다.</p>
                            <p>• 중도 해지 시 약정 금리가 아닌 중도해지 금리가 적용됩니다.</p>
                        </div>

                        <div className="flex gap-3">
                            <button
                                onClick={() => setIsModalOpen(false)}
                                className="w-1/3 py-3 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold rounded-2xl text-xs"
                            >
                                취소
                            </button>
                            <Button onClick={handleConfirm} className="w-2/3 py-3 text-xs">
                                동의하고 확정하기 ✨
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}