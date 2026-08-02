import React from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import { authStorage } from '../utils/storage';

export default function MyPage({ user, onReSimulate, onLogout }) {
    const savedRoadmap = authStorage.getRoadmap();
    const selectedOption = authStorage.getSelectedOption();

    return (
        <div className="max-w-3xl mx-auto space-y-6 animate-fadeIn pb-12">
            {/* 유저 프로필 헤더 */}
            <div className="bg-white p-6 rounded-[2rem] border border-amber-200 shadow-sm flex justify-between items-center">
                <div className="flex items-center gap-4">
                    <div className="w-12 h-12 bg-amber-300 rounded-2xl flex items-center justify-center text-xl font-black text-amber-950">
                        👤
                    </div>
                    <div>
                        <h2 className="text-lg font-black text-amber-950">{user?.name || '유저'}님</h2>
                        <p className="text-xs text-amber-800/60 font-semibold">{user?.email}</p>
                    </div>
                </div>
                <button
                    onClick={onLogout}
                    className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-bold rounded-xl transition-all"
                >
                    로그아웃
                </button>
            </div>

            {/* 내 저장된 로드맵 요약 */}
            <Card title="내 확정 저축 로드맵" icon="📜" subtitle="최근에 최종 동의 및 확정한 로드맵 정보입니다.">
                {savedRoadmap ? (
                    <div className="space-y-4 my-4">
                        <div className="bg-amber-50 p-5 rounded-2xl border border-amber-200 space-y-2">
                            <span className="text-xs font-black text-amber-900 bg-amber-200/80 px-2.5 py-1 rounded-md inline-block">
                                선택 플랜: {selectedOption?.optionType || '맞춤 추천'}
                            </span>
                            <div className="flex justify-between items-baseline pt-2">
                                <span className="text-xs text-amber-800/80 font-bold">목표 풍차 금액</span>
                                <span className="text-xl font-black text-amber-950">
                                    {Math.floor((selectedOption?.expectedFinalAmount || 0) / 10000).toLocaleString()}만원
                                </span>
                            </div>
                            <div className="flex justify-between items-baseline">
                                <span className="text-xs text-amber-800/80 font-bold">실질 기대 수익률 E[r]</span>
                                <span className="text-base font-black text-blue-600">
                                    {(selectedOption?.weightedExpectedRate || 0).toFixed(2)}%
                                </span>
                            </div>
                        </div>

                        <div className="flex gap-3 pt-2">
                            <Button onClick={onReSimulate} className="w-full py-3 text-xs">
                                🔄 새로운 조건으로 다시 진단하기
                            </Button>
                        </div>
                    </div>
                ) : (
                    <div className="py-12 text-center space-y-4">
                        <p className="text-xs font-bold text-amber-800/60">
                            아직 확정된 저축 로드맵이 없습니다.
                        </p>
                        <Button onClick={onReSimulate} className="py-3 px-6 text-xs">
                            🌱 실질 금리 진단하러 가기
                        </Button>
                    </div>
                )}
            </Card>
        </div>
    );
}