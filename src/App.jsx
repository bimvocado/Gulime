import React, { useState, useEffect } from 'react';
import Header from './components/common/Header';
import IntroPage from './pages/IntroPage';
import SimulationPage from './pages/SimulationPage';
import OptionsPage from './pages/OptionsPage';
import RoadmapPage from './pages/RoadmapPage';
import MyPage from './pages/MyPage';
import { authStorage } from './utils/storage';

export default function App() {
    const [user, setUser] = useState(null);
    const [activeTab, setActiveTab] = useState('simulation');
    const [userProfile, setUserProfile] = useState(null);
    const [selectedOption, setSelectedOption] = useState(null);

    useEffect(() => {
        const savedUser = authStorage.getUser();
        const savedProfile = authStorage.getProfile();
        const savedOption = authStorage.getSelectedOption();

        if (savedUser) setUser(savedUser);
        if (savedProfile) setUserProfile(savedProfile);
        if (savedOption) setSelectedOption(savedOption);

        // 이전 세션 잔재로 인한 Step 3 직행 방지 (항상 Step 1부터 시작)
        setActiveTab('simulation');
    }, []);

    const handleLoginStart = (userData) => {
        setUser(userData);
        // 로그인 시 이전 시뮬레이션/옵션 캐시 초기화
        localStorage.removeItem('gulimi_simulation_state');
        localStorage.removeItem('gulimi_options_state');
        localStorage.removeItem('gulimi_selected_option');
        authStorage.setSelectedOption(null);
        authStorage.setRoadmap(null);

        setUserProfile(null);
        setActiveTab('simulation');
    };

    const handleSimulationComplete = (profileData) => {
        setUserProfile(profileData);
        authStorage.setProfile(profileData);

        setSelectedOption(null);
        authStorage.setSelectedOption(null);
        authStorage.setRoadmap(null);
        localStorage.removeItem('gulimi_options_state');
        localStorage.removeItem('gulimi_selected_option');

        setActiveTab('options');
    };

    const handleOptionSelect = (option) => {
        setSelectedOption(option);
        authStorage.setSelectedOption(option);
        setActiveTab('roadmap');
    };

    const handleLogout = () => {
        authStorage.clear();
        localStorage.clear();
        setUser(null);
        setUserProfile(null);
        setSelectedOption(null);
        setActiveTab('simulation');
    };

    if (!user) {
        return (
            <div className="min-h-screen bg-amber-50/40 text-amber-950 font-sans pb-20 selection:bg-amber-200">
                <main className="px-4 pt-6">
                    <IntroPage onStart={handleLoginStart} />
                </main>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-amber-50/40 text-amber-950 font-sans pb-20 selection:bg-amber-200">
            {/* 💡 userProfile props 추가 완료! */}
            <Header
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                user={user}
                userProfile={userProfile}
                onLogout={handleLogout}
            />

            <main className="px-4 pt-6">
                {activeTab === 'simulation' && (
                    <SimulationPage
                        initialProfile={userProfile}
                        onNext={handleSimulationComplete}
                    />
                )}

                {activeTab === 'options' && (
                    <OptionsPage
                        userProfile={userProfile}
                        onNext={handleOptionSelect}
                        onPrev={() => setActiveTab('simulation')}
                        onGoSimulation={() => setActiveTab('simulation')}
                    />
                )}

                {activeTab === 'roadmap' && (
                    <RoadmapPage
                        userProfile={userProfile}
                        selectedOption={selectedOption}
                        onPrev={() => setActiveTab('options')}
                        onConfirmSuccess={() => setActiveTab('mypage')}
                    />
                )}

                {activeTab === 'mypage' && (
                    <MyPage
                        user={user}
                        onReSimulate={() => setActiveTab('simulation')}
                        onLogout={handleLogout}
                    />
                )}
            </main>
        </div>
    );
}