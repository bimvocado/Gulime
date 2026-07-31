import React from 'react';

export default function Header({ activeTab, setActiveTab }) {
    const tabs = [
        { id: 'simulation', label: '1. 자산 굴리기', emoji: '🎲' },
        { id: 'options', label: '2. 3가지 맞춤 플랜', emoji: '✨' },
        { id: 'roadmap', label: '3. 12개월 지도', emoji: '🗺️' }
    ];

    return (
        <header className="bg-white/80 backdrop-blur-md sticky top-0 z-50 border-b border-amber-100 shadow-sm">
            <div className="max-w-5xl mx-auto px-6 h-20 flex items-center justify-between">
                {/* 귀여운 굴리미 로고 */}
                <div className="flex items-center gap-3 cursor-pointer" onClick={() => setActiveTab('simulation')}>
                    <div className="w-11 h-11 bg-amber-300 rounded-2xl flex items-center justify-center text-xl font-black text-slate-800 shadow-md shadow-amber-200/50 transform hover:rotate-6 transition">
                        🟡
                    </div>
                    <div>
            <span className="text-xl font-black text-amber-950 tracking-tight flex items-center gap-1.5">
              굴리미 <span className="text-[11px] bg-amber-100 text-amber-800 font-bold px-2 py-0.5 rounded-full border border-amber-200">Gulime AI</span>
            </span>
                        <p className="text-[11px] text-amber-600/70 font-medium">내 자산 차곡차곡 굴리기</p>
                    </div>
                </div>

                {/* 탭 네비게이션 */}
                <nav className="flex bg-amber-50/80 p-1.5 rounded-2xl border border-amber-100/80">
                    {tabs.map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setActiveTab(tab.id)}
                            className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                                activeTab === tab.id
                                    ? 'bg-amber-300 text-amber-950 shadow-sm shadow-amber-200 scale-105'
                                    : 'text-amber-800/60 hover:text-amber-900 hover:bg-amber-100/50'
                            }`}
                        >
                            <span>{tab.emoji}</span>
                            <span>{tab.label}</span>
                        </button>
                    ))}
                </nav>
            </div>
        </header>
    );
}