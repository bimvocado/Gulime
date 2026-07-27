import React, { useState } from 'react';
import Header from './components/common/Header';
import SimulationPage from './pages/SimulationPage';
import OptionsPage from './pages/OptionsPage';
import RoadmapPage from './pages/RoadmapPage';

export default function App() {
    const [activeTab, setActiveTab] = useState('simulation');

    return (
        <div className="min-h-screen bg-amber-50/40 text-amber-950 font-sans pb-20 selection:bg-amber-200">
            {/* 헤더 */}
            <Header activeTab={activeTab} setActiveTab={setActiveTab} />

            {/* 메인 콘텐츠 */}
            <main className="px-4 pt-6">
                {activeTab === 'simulation' && <SimulationPage onNext={() => setActiveTab('options')} />}
                {activeTab === 'options' && <OptionsPage onNext={() => setActiveTab('roadmap')} />}
                {activeTab === 'roadmap' && <RoadmapPage />}
            </main>
        </div>
    );
}