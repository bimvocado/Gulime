import React, { useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import { authStorage } from '../utils/storage';

export default function IntroPage({ onStart }) {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');

    const handleLogin = (e) => {
        e.preventDefault();
        const userData = {
            name: name.trim() || '굴리미 유저',
            email: email.trim() || 'user@gulimi.com',
            loggedInAt: new Date().toISOString()
        };
        authStorage.setUser(userData);
        onStart(userData);
    };

    return (
        <div className="max-w-md mx-auto py-12 space-y-6 animate-fadeIn">
            <div className="text-center space-y-3">
                <span className="text-5xl inline-block animate-bounce">🪙</span>
                <h1 className="text-3xl font-black text-amber-950 tracking-tight">
                    굴리미 <span className="text-amber-600">AI</span>
                </h1>
                <p className="text-xs text-amber-800/80 font-semibold">
                    광고 금리 말고, 내가 진짜 받을 실질 기대금리 진단
                </p>
            </div>

            <Card className="p-8 space-y-6">
                <form onSubmit={handleLogin} className="space-y-4">
                    <div>
                        <label className="block text-xs font-extrabold text-amber-900 mb-1.5">
                            이름 (닉네임)
                        </label>
                        <input
                            type="text"
                            placeholder="예: 홍길동"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            className="w-full px-4 py-3 bg-amber-50/50 border border-amber-200 rounded-2xl text-xs font-bold text-amber-950 focus:outline-none focus:border-amber-400"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-xs font-extrabold text-amber-900 mb-1.5">
                            이메일 주소
                        </label>
                        <input
                            type="email"
                            placeholder="example@gulimi.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            className="w-full px-4 py-3 bg-amber-50/50 border border-amber-200 rounded-2xl text-xs font-bold text-amber-950 focus:outline-none focus:border-amber-400"
                            required
                        />
                    </div>

                    <Button type="submit" className="w-full py-4 text-sm font-black mt-2">
                        🚀 진단 시작하기
                    </Button>
                </form>
            </Card>

            <div className="text-center">
                <p className="text-[11px] text-amber-800/60 font-medium">
                    별도의 복잡한 절차 없이 이메일 입력으로 바로 시뮬레이션이 가능합니다.
                </p>
            </div>
        </div>
    );
}