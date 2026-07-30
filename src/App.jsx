import React, { useState } from 'react';
import Header from './components/common/Header';
import SimulationPage from './pages/SimulationPage';
import OptionsPage from './pages/OptionsPage';
import RoadmapPage from './pages/RoadmapPage';

export default function App() {
    const [activeTab, setActiveTab] = useState('simulation');
    const [userProfile, setUserProfile] = useState(null);
    const [selectedOption, setSelectedOption] = useState(null);

    const handleSimulationComplete = (profileData) => {
        setUserProfile(profileData);
        setActiveTab('options');
    };

    const handleOptionSelect = (option) => {
        setSelectedOption(option);
        setActiveTab('roadmap');
    };

    return (
        <div className="min-h-screen bg-amber-50/40 text-amber-950 font-sans pb-20 selection:bg-amber-200">
            <Header activeTab={activeTab} setActiveTab={setActiveTab} />

            <main className="px-4 pt-6">
                {activeTab === 'simulation' && (
                    <SimulationPage onNext={handleSimulationComplete} />
                )}

                {activeTab === 'options' && (
                    <OptionsPage
                        userProfile={userProfile}
                        onNext={handleOptionSelect}
                        onPrev={() => setActiveTab('simulation')}
                    />
                )}

                {activeTab === 'roadmap' && (
                    <RoadmapPage
                        userProfile={userProfile}
                        selectedOption={selectedOption}
                        onPrev={() => setActiveTab('options')}
                    />
                )}
            </main>
        </div>
    );
}