package com.ridetune.app.domain

/** 얼마나 민감한가. 진단명은 묻지 않고 "무엇이 힘든지"만 받는다. */
enum class Sensitivity { NONE, SOME, HIGH }

/** 마음이 편해지는 무드등 색(ARGB, 알파 0xFF 고정 — Vehicle SDK 규격). */
enum class CalmColor(val argb: Int) {
    BLUE(0xFF4A7BD0.toInt()),
    GREEN(0xFF4CAF7A.toInt()),
    AMBER(0xFFE0A040.toInt()),
    LAVENDER(0xFF9A86D8.toInt()),
}

/**
 * 탑승자 한 명의 감각 프로필. 지금은 차량 앱에서 직접 만들고, 나중에 휴대폰에서 만든 것을 받는다.
 */
data class SensoryProfile(
    /** 소음·갑작스러운 소리 */
    val sound: Sensitivity = Sensitivity.NONE,
    /** 밝기·눈부심·깜빡임 */
    val light: Sensitivity = Sensitivity.NONE,
    /** 바람·온도·공기 */
    val climate: Sensitivity = Sensitivity.NONE,
    /** 예측하지 못한 변화 — 미리 알려 주기(TTS)에서 쓴다 */
    val change: Sensitivity = Sensitivity.NONE,
    val preferredTempC: Float = 22f,
    val calmColor: CalmColor = CalmColor.BLUE,
)
