import React from 'react';

export default function Header({ activeTab, setActiveTab }) {
    const tabs = [
        { id: 'simulation', label: '1. 자산 진단', emoji: '🎲' },
        { id: 'options', label: '2. 맞춤 플랜', emoji: '✨' },
        { id: 'roadmap', label: '3. 로드맵', emoji: '🗺️' }
    ];

    return (
        /* 스마트폰 화면비 고정 (Max-Width 430px 모바일 상단 바) */
        <header className="sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-amber-100/80 max-w-[430px] mx-auto w-full">
            <div className="px-4 py-3">
                {/* 상단: 로고 및 타이틀 */}
                <div className="flex items-center justify-between mb-2.5">
                    <div
                        className="flex items-center gap-2 cursor-pointer active:scale-95 transition-transform"
                        onClick={() => setActiveTab('simulation')}
                    >
                        <div className="w-8 h-8 bg-amber-300 rounded-xl flex items-center justify-center text-sm font-black text-slate-800 shadow-sm">
                            🟡
                        </div>
                        <div>
                            <div className="flex items-center gap-1.5">
                                <span className="text-base font-black text-amber-950 tracking-tight">굴리미</span>
                                <span className="text-[9px] bg-amber-100 text-amber-800 font-extrabold px-1.5 py-0.2 rounded-md">
                                    AI
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* 현재 진행 중인 단계 표시 */}
                    <div className="text-[11px] font-extrabold text-amber-700 bg-amber-50 px-2.5 py-1 rounded-full border border-amber-200/60">
                        {tabs.find(t => t.id === activeTab)?.label}
                    </div>
                </div>

                {/* 하단: 모바일 최적화 3단계 스텝 탭 (터치영역 확보) */}
                <nav className="grid grid-cols-3 gap-1 bg-amber-50/80 p-1 rounded-xl border border-amber-100/80">
                    {tabs.map((tab) => {
                        const isActive = activeTab === tab.id;
                        return (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id)}
                                className={`py-1.5 px-1 rounded-lg text-[11px] font-bold transition-all flex items-center justify-center gap-1 active:scale-95 ${
                                    isActive
                                        ? 'bg-amber-300 text-amber-950 shadow-sm font-black'
                                        : 'text-amber-800/60 hover:text-amber-900'
                                }`}
                            >
                                <span className="text-xs">{tab.emoji}</span>
                                <span className="truncate">{tab.label.split('. ')[1]}</span>
                            </button>
                        );
                    })}
                </nav>
            </div>
        </header>
    );
}