package com.example.demo.savings.service;

import com.example.demo.savings.api.ProfileRequest;
import com.example.demo.savings.domain.UserProfile;

final class ProfileMapper {

    private ProfileMapper() {
    }

    static UserProfile toDomain(ProfileRequest profile) {
        return new UserProfile(
                profile.employment(),
                profile.salaryTransferable(),
                profile.lumpSum(),
                profile.emergencyFund(),
                profile.monthlySaving(),
                profile.targetMonths(), // 👈 🎯 targ 오타 수정 및 targetMonths 순서에 맞춰 배치
                profile.cardSpend6m(),
                profile.cardBudgetCap(),
                profile.existingBanks()
        );
    }
}

