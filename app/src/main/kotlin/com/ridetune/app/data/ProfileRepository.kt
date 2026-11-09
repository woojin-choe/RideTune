package com.ridetune.app.data

import android.content.Context
import com.ridetune.app.domain.CalmColor
import com.ridetune.app.domain.Sensitivity
import com.ridetune.app.domain.SensoryProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 감각 프로필 저장소. 지금은 기기 안(SharedPreferences)에만 둔다.
 * 휴대폰 연동이 생기면 받은 프로필을 [save]로 넣으면 화면과 적용 로직은 그대로 쓴다.
 */
class ProfileRepository(context: Context) {

    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<SensoryProfile> = _profile.asStateFlow()

    fun save(profile: SensoryProfile) {
        prefs.edit()
            .putString(KEY_SOUND, profile.sound.name)
            .putString(KEY_LIGHT, profile.light.name)
            .putString(KEY_CLIMATE, profile.climate.name)
            .putString(KEY_CHANGE, profile.change.name)
            .putFloat(KEY_TEMP, profile.preferredTempC)
            .putString(KEY_COLOR, profile.calmColor.name)
            .apply()
        _profile.value = profile
    }

    private fun load(): SensoryProfile {
        val default = SensoryProfile()
        return SensoryProfile(
            sound = enumOr(KEY_SOUND, default.sound),
            light = enumOr(KEY_LIGHT, default.light),
            climate = enumOr(KEY_CLIMATE, default.climate),
            change = enumOr(KEY_CHANGE, default.change),
            preferredTempC = prefs.getFloat(KEY_TEMP, default.preferredTempC),
            calmColor = enumOr(KEY_COLOR, default.calmColor),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(key: String, fallback: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        const val KEY_SOUND = "sound"
        const val KEY_LIGHT = "light"
        const val KEY_CLIMATE = "climate"
        const val KEY_CHANGE = "change"
        const val KEY_TEMP = "preferredTempC"
        const val KEY_COLOR = "calmColor"
    }
}

/** 화면에서 쓰는 민감도 영역. 저장 키와 무관하다. */
enum class SenseArea { SOUND, LIGHT, CLIMATE, CHANGE }

fun SensoryProfile.level(area: SenseArea): Sensitivity = when (area) {
    SenseArea.SOUND -> sound
    SenseArea.LIGHT -> light
    SenseArea.CLIMATE -> climate
    SenseArea.CHANGE -> change
}

fun SensoryProfile.withLevel(area: SenseArea, level: Sensitivity): SensoryProfile = when (area) {
    SenseArea.SOUND -> copy(sound = level)
    SenseArea.LIGHT -> copy(light = level)
    SenseArea.CLIMATE -> copy(climate = level)
    SenseArea.CHANGE -> copy(change = level)
}
