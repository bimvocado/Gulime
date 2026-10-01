

export const authStorage = {
    // 유저 로그인 상태
    setUser: (userData) => localStorage.setItem('gulimi_user', JSON.stringify(userData)),
    getUser: () => JSON.parse(localStorage.getItem('gulimi_user') || 'null'),

    // 유저가 진단한 프로필 데이터
    setProfile: (profile) => localStorage.setItem('gulimi_profile', JSON.stringify(profile)),
    getProfile: () => JSON.parse(localStorage.getItem('gulimi_profile') || 'null'),

    // 유저가 선택한 옵션 데이터
    setSelectedOption: (option) => localStorage.setItem('gulimi_option', JSON.stringify(option)),
    getSelectedOption: () => JSON.parse(localStorage.getItem('gulimi_option') || 'null'),

    // 최종 확정된 로드맵 결과
    setRoadmap: (roadmap) => localStorage.setItem('gulimi_roadmap', JSON.stringify(roadmap)),
    getRoadmap: () => JSON.parse(localStorage.getItem('gulimi_roadmap') || 'null'),

    // 전체 초기화 (로그아웃 시)
    clear: () => localStorage.clear()
};