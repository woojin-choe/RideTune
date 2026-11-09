package com.ridetune.app.domain

/** 감각 프로필을 차 안 목표 설정으로 바꾼다. 차량과 무관한 순수 계산이라 단위 테스트로 검증한다. */
object CabinPlanner {

    /** 탑승할 때 적용하는 평소 설정. 민감하지 않은 영역은 건드리지 않는다. */
    fun comfort(profile: SensoryProfile): CabinTarget {
        val anySensitive = listOf(profile.sound, profile.light, profile.climate, profile.change)
            .any { it != Sensitivity.NONE }
        if (!anySensitive) return CabinTarget()

        val fanLimits = listOfNotNull(
            when (profile.sound) {
                Sensitivity.HIGH -> 0.3f
                Sensitivity.SOME -> 0.5f
                Sensitivity.NONE -> null
            },
            when (profile.climate) {
                Sensitivity.HIGH -> 0.25f
                Sensitivity.SOME -> 0.5f
                Sensitivity.NONE -> null
            },
        )

        return CabinTarget(
            ambientBrightness = when (profile.light) {
                Sensitivity.HIGH -> 0.2f
                Sensitivity.SOME -> 0.4f
                Sensitivity.NONE -> null
            },
            ambientColor = profile.calmColor.argb,
            cabinLightOff = profile.light == Sensitivity.HIGH,
            smartGlassDim = when (profile.light) {
                Sensitivity.HIGH -> 0.7f
                Sensitivity.SOME -> 0.4f
                Sensitivity.NONE -> null
            },
            temperatureC = profile.preferredTempC.takeIf { profile.climate != Sensitivity.NONE },
            fanSpeed = fanLimits.minOrNull(),
            recirculation = true.takeIf { profile.sound != Sensitivity.NONE },
            closeWindows = profile.sound == Sensitivity.HIGH,
        )
    }

    /** 진정 모드: 프로필과 상관없이 모든 자극을 한 번에 낮춘다. 온도와 색만 프로필을 따른다. */
    fun calm(profile: SensoryProfile): CabinTarget = CabinTarget(
        ambientBrightness = 0.1f,
        ambientColor = profile.calmColor.argb,
        cabinLightOff = true,
        smartGlassDim = 0.8f,
        temperatureC = profile.preferredTempC,
        fanSpeed = 0.2f,
        recirculation = true,
        closeWindows = true,
    )
}
