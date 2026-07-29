import React, { useState } from 'react';

export default function OnboardingForm({ onSubmit }) {
    const [formData, setFormData] = useState({
        employmentType: 'EMPLOYED', // 근로형태
        totalLumpSum: 10000000,      // 목돈
        emergencyFund: 2000000,      // 비상금
        monthlySavingsCapacity: 1000000, // 저축 여력
        recent6mCardSpend: [500000, 450000, 520000, 480000, 510000, 490000], // 카드 6개월
        primaryBank: 'KB',           // 주요 거래 은행
        salaryTransferAvailable: true // 급여이체 가능 여부
    });

    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    const handleCardSpendChange = (index, value) => {
        const updatedSpends = [...formData.recent6mCardSpend];
        updatedSpends[index] = Number(value) || 0;
        setFormData(prev => ({ ...prev, recent6mCardSpend: updatedSpends }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        if (onSubmit) {
            onSubmit(formData);
        }
    };

    return (
        <div style={{ maxWidth: '600px', margin: '0 auto', padding: '20px' }}>
            <h2>📋 사용자 맞춤 시뮬레이션 온보딩</h2>
            <form onSubmit={handleSubmit}>

                {/* 1. 근로형태 */}
                <div style={{ marginBottom: '15px' }}>
                    <label><strong>근로형태</strong></label>
                    <select
                        value={formData.employmentType}
                        onChange={(e) => handleChange('employmentType', e.target.value)}
                        style={{ width: '100%', padding: '8px', marginTop: '5px' }}
                    >
                        <option value="EMPLOYED">직장인 (급여소득자)</option>
                        <option value="SELF_EMPLOYED">자영업자 / 사업자</option>
                        <option value="FREELANCER">프리랜서 / 기타</option>
                    </select>
                </div>

                {/* 2. 목돈 및 비상금 */}
                <div style={{ display: 'flex', gap: '10px', marginBottom: '15px' }}>
                    <div style={{ flex: 1 }}>
                        <label><strong>보유 목돈 (원)</strong></label>
                        <input
                            type="number"
                            value={formData.totalLumpSum}
                            onChange={(e) => handleChange('totalLumpSum', Number(e.target.value))}
                            style={{ width: '100%', padding: '8px', marginTop: '5px' }}
                        />
                    </div>
                    <div style={{ flex: 1 }}>
                        <label><strong>비상금 설정 (원)</strong></label>
                        <input
                            type="number"
                            value={formData.emergencyFund}
                            onChange={(e) => handleChange('emergencyFund', Number(e.target.value))}
                            style={{ width: '100%', padding: '8px', marginTop: '5px' }}
                        />
                    </div>
                </div>

                {/* 3. 월 저축 여력 & 주요 거래 은행 */}
                <div style={{ display: 'flex', gap: '10px', marginBottom: '15px' }}>
                    <div style={{ flex: 1 }}>
                        <label><strong>월 저축 가능 금액 (원)</strong></label>
                        <input
                            type="number"
                            value={formData.monthlySavingsCapacity}
                            onChange={(e) => handleChange('monthlySavingsCapacity', Number(e.target.value))}
                            style={{ width: '100%', padding: '8px', marginTop: '5px' }}
                        />
                    </div>
                    <div style={{ flex: 1 }}>
                        <label><strong>주요 거래 은행</strong></label>
                        <select
                            value={formData.primaryBank}
                            onChange={(e) => handleChange('primaryBank', e.target.value)}
                            style={{ width: '100%', padding: '8px', marginTop: '5px' }}
                        >
                            <option value="KB">KB국민은행</option>
                            <option value="SHINHAN">신한은행</option>
                            <option value="WOORI">우리은행</option>
                            <option value="HANA">하나은행</option>
                        </select>
                    </div>
                </div>

                {/* 4. 최근 6개월 카드 사용 내역 */}
                <div style={{ marginBottom: '20px' }}>
                    <label><strong>최근 6개월 카드 사용 금액 (원)</strong></label>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px', marginTop: '8px' }}>
                        {formData.recent6mCardSpend.map((spend, idx) => (
                            <div key={idx}>
                                <span style={{ fontSize: '12px', color: '#666' }}>{idx + 1}개월 전</span>
                                <input
                                    type="number"
                                    value={spend}
                                    onChange={(e) => handleCardSpendChange(idx, e.target.value)}
                                    style={{ width: '100%', padding: '6px' }}
                                />
                            </div>
                        ))}
                    </div>
                </div>

                {/* 제출 버튼 */}
                <button
                    type="submit"
                    style={{ width: '100%', padding: '12px', backgroundColor: '#FFBC00', color: '#000', border: 'none', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
                >
                    맞춤 우대금리 시뮬레이션 시작하기
                </button>
            </form>
        </div>
    );
}