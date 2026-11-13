package com.humanfirst.ridetune.vehicle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable enum class Feature(val label: String) {
    POWER("공조 전원"), AUTO("자동 공조"), AC("에어컨"), TEMPERATURE("설정 온도"), FAN("바람 세기"), DIRECTION("송풍 방향"),
    COLOR_AUTO("주행 모드 연동 색상"), LIGHT("앰비언트 조명"), COLOR("조명 색상"), BRIGHTNESS("조명 밝기")
}
@Serializable data class Preferences(
    val temperatureCelsius: Float? = null, val fanPreference: String? = null,
    val airflowPreference: String? = null, val power: Boolean? = null, val auto: Boolean? = null, val ac: Boolean? = null,
    val ambientEnabled: Boolean? = null, val ambientBrightnessPercent: Int? = null, val ambientColorHex: String? = null,
    val ambientColorAuto: Boolean? = null
) {
    fun values(): Map<Feature, String> = buildMap {
        power?.let { put(Feature.POWER, it.toString()) }; auto?.let { put(Feature.AUTO,it.toString()) }; ac?.let { put(Feature.AC,it.toString()) }
        temperatureCelsius?.let { put(Feature.TEMPERATURE,it.toString()) }; fanPreference?.let { put(Feature.FAN,it) }
        airflowPreference?.let { put(Feature.DIRECTION,it) }; ambientEnabled?.let { put(Feature.LIGHT,if(it) "ON" else "OFF") }
        ambientBrightnessPercent?.let { put(Feature.BRIGHTNESS,it.toString()) }; ambientColorHex?.let { put(Feature.COLOR,it.uppercase()) }
        ambientColorAuto?.let { put(Feature.COLOR_AUTO,it.toString()) }
    }
}
@Serializable data class PassengerProfile(
    val schemaVersion: Int = 1, val profileId: String, val displayName: String,
    val avatarKey: String = "leaf", val updatedAt: Long = System.currentTimeMillis(),
    val preferredSeat: String = "ROW_1_RIGHT", val preferences: Preferences,
    val interestTopic: String = "바다와 고래",
    val comfortPhrase: String = "조금만 더 가면 안전하게 도착해요",
    val weatherScene: String = "맑은 날"
)
val profileJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
object ProfileCodec {
    val seats = listOf("ROW_1_LEFT","ROW_1_RIGHT","ROW_2_LEFT","ROW_2_CENTER","ROW_2_RIGHT","ROW_3_LEFT","ROW_3_CENTER","ROW_3_RIGHT")
    fun decode(text: String): PassengerProfile {
        require(text.toByteArray().size <= 16384) { "프로필은 16KB 이하여야 합니다" }
        return profileJson.decodeFromString<PassengerProfile>(text).also { validate(it) }
    }
    fun validate(p: PassengerProfile) {
        require(p.schemaVersion == 1) { "지원하지 않는 프로필 버전" }
        require(p.profileId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "프로필 ID 형식 오류" }
        require(p.displayName.isNotBlank() && p.displayName.length <= 30) { "별명은 1~30자" }
        require(p.interestTopic.length <= 60) { "관심 주제는 60자 이하" }
        require(p.comfortPhrase.length <= 100) { "안심 문구는 100자 이하" }
        require(p.weatherScene.length <= 40) { "창밖 상황은 40자 이하" }
        require(p.preferredSeat in seats) { "지원하지 않는 좌석" }
        with(p.preferences) {
            require(temperatureCelsius == null || temperatureCelsius.isFinite() && temperatureCelsius in 10f..40f) { "온도는 10~40도 범위" }
            require(fanPreference == null || fanPreference in listOf("OFF","LOW","MEDIUM","HIGH")) { "풍량 형식 오류" }
            require(airflowPreference == null || airflowPreference in listOf("FACE","FLOOR","FACE_AND_FLOOR")) { "송풍 방향 형식 오류" }
            require(ambientBrightnessPercent == null || ambientBrightnessPercent in 0..100) { "밝기는 0~100%" }
            require(ambientColorHex == null || ambientColorHex.matches(Regex("#[0-9a-fA-F]{6}"))) { "색상은 #RRGGBB 형식" }
            require(!(auto == true && (fanPreference != null || airflowPreference != null))) { "자동 공조와 수동 바람 설정을 동시에 지정할 수 없습니다" }
            require(!(power == false && (temperatureCelsius != null || fanPreference != null || airflowPreference != null || ac == true || auto == true))) { "공조 OFF와 공조 동작 설정을 함께 지정할 수 없습니다" }
            require(!(ambientColorAuto == true && ambientColorHex != null)) { "자동 색상과 고정 색상을 동시에 지정할 수 없습니다" }
        }
    }
    fun examples() = listOf(
        PassengerProfile(profileId="sample-sea",displayName="바다",preferences=Preferences(22f,"LOW","FLOOR",true,false,true,true,20,"#5588CC",false),interestTopic="바다와 고래",comfortPhrase="지금은 안전하게 가는 중이고, 곧 도착해요",weatherScene="비 오는 길"),
        PassengerProfile(profileId="sample-forest",displayName="숲",preferences=Preferences(23f,"LOW",null,true,false,null,true,15,"#70B89B",false),interestTopic="공룡과 나무",comfortPhrase="엄마 아빠랑 함께 안전하게 이동하고 있어요",weatherScene="맑은 날"),
        PassengerProfile(profileId="sample-light",displayName="온유",preferences=Preferences(ambientEnabled=true,ambientBrightnessPercent=35,ambientColorHex="#D2A66B",ambientColorAuto=false),interestTopic="기차와 역 이름",comfortPhrase="이번 구간만 지나면 바로 도착해요",weatherScene="깜깜한 밤·터널")
    )
}
@Serializable data class Capability(val feature: Feature, val area: String, val access: String,
    val min: Double? = null, val max: Double? = null, val step: Double? = null,
    val options: List<String> = emptyList(), val error: String? = null) {
    val readable get() = error == null && access in listOf("READ","READ_WRITE")
    val writable get() = readable && access == "READ_WRITE"
    val shared get() = area == "GLOBAL" || area.contains('|')
}
fun areaLabel(area: String): String = if(area == "GLOBAL") "차량 전체" else area.split('|').joinToString(" · ") {
    when(it) { "ROW_1_LEFT" -> "앞좌석 왼쪽"; "ROW_1_RIGHT" -> "앞좌석 오른쪽"; "ROW_1_CENTER" -> "앞좌석 중앙"; "ROW_2_LEFT" -> "2열 왼쪽"; "ROW_2_CENTER" -> "2열 중앙"; "ROW_2_RIGHT" -> "2열 오른쪽"; else -> it.replace("ROW_3_","3열 ") }
}
@Serializable data class PlanItem(val feature: Feature, val area: String, val before: String?, val target: String?, val reason: String? = null, val note: String = "") {
    val actionable get() = reason == null && before != null && target != null && !sameValue(feature,before,target)
}
fun sameValue(f: Feature, a: String?, b: String?): Boolean = a != null && b != null && if(f == Feature.TEMPERATURE) abs((a.toDoubleOrNull() ?: -999.0)-(b.toDoubleOrNull() ?: 999.0)) < 0.05 else a == b
fun prettyValue(f: Feature, v: String?): String = when {
    v == null -> "확인 안 됨"
    f == Feature.TEMPERATURE -> "$v °C"
    f == Feature.BRIGHTNESS -> "$v (차량 값)"
    f == Feature.FAN -> "$v 단계"
    v == "true" || v == "ON" -> "켜짐"
    v == "false" || v == "OFF" -> "꺼짐"
    v == "FACE" -> "얼굴 방향"
    v == "FLOOR" -> "발 방향"
    v == "FACE_AND_FLOOR" -> "얼굴 + 발"
    else -> v
}
fun requiresColorAutoCheck(caps: List<Capability>): Boolean {
    val auto = caps.filter { it.feature == Feature.COLOR_AUTO }
    return auto.isEmpty() || auto.any {
        it.access != "NONE" || (it.error != null && !it.error.contains("ERROR_NOT_SUPPORTED_PROPERTY"))
    }
}
fun buildPlan(p: PassengerProfile, caps: List<Capability>, current: Map<String,String>): List<PlanItem> = p.preferences.values().map { (f, request) ->
    val cap = caps.firstOrNull { it.feature == f && (it.area == "GLOBAL" || p.preferredSeat in it.area.split('|')) }
    val before = cap?.let { current["${f.name}:${it.area}"] }
    var target = request
    var reason = when { cap == null -> "선택 좌석에서 지원하지 않음"; cap.error != null -> cap.error; !cap.writable -> "읽기·쓰기 모두 가능한 항목만 적용"; before == null -> "현재값을 읽지 못해 복원 불가"; else -> null }
    if(reason == null && cap != null) {
        when(f) {
            Feature.FAN, Feature.BRIGHTNESS -> {
                if(cap.min == null || cap.max == null || cap.min > cap.max) reason = "허용 범위 확인 불가"
                else {
                    val fraction = if(f == Feature.BRIGHTNESS) request.toDouble()/100 else when(request) { "LOW" -> 0.0; "MEDIUM" -> 0.5; "HIGH" -> 1.0; else -> 0.0 }
                    val lower = if(f == Feature.FAN) maxOf(1.0,cap.min) else cap.min
                    val n = if(f == Feature.FAN && request == "OFF") 0 else (lower+(cap.max-lower)*fraction).roundToInt()
                    if(n.toDouble() !in cap.min..cap.max) reason = "요청 단계는 차량에서 지원하지 않음" else target = n.toString()
                }
            }
            Feature.TEMPERATURE -> {
                val n = request.toDouble()
                if(cap.min == null || cap.max == null || n !in cap.min..cap.max) reason = "요청 온도는 차량 지원 범위 밖 (${cap.min}~${cap.max}°C)"
                else if(cap.step != null && cap.step > 0 && abs((n-cap.min)/cap.step-((n-cap.min)/cap.step).roundToInt()) > 0.01) reason = "차량 온도 간격 ${cap.step}°C에 맞춰 수정 필요"
            }
            Feature.DIRECTION -> if(target !in cap.options) reason = "지원 송풍 모드 목록이 없어 적용 보류"
            else -> if(cap.options.isNotEmpty() && target !in cap.options) reason = "차량이 지원하지 않는 설정"
        }
    }
    PlanItem(f,cap?.area ?: p.preferredSeat,before,target,reason,if(cap?.shared == true) "공유 구역에 함께 적용" else "")
}.let { items ->
    // Dependencies must be explicitly available and enabled; never silently overwrite modes.
    items.map { item ->
        fun effective(f: Feature): String? = items.find { it.feature == f && it.reason == null }?.target ?: caps.firstOrNull { it.feature == f && (it.area == "GLOBAL" || p.preferredSeat in it.area.split('|')) }?.let { current["${f.name}:${it.area}"] }
        val block = when {
            item.feature in listOf(Feature.FAN,Feature.DIRECTION) && effective(Feature.AUTO) != "false" -> "수동 바람 적용에는 자동 공조 끄기 확인 필요"
            item.feature in listOf(Feature.TEMPERATURE,Feature.FAN,Feature.DIRECTION,Feature.AC,Feature.AUTO) && effective(Feature.POWER) != "true" -> "공조 전원 켜기 확인 필요"
            item.feature == Feature.COLOR && requiresColorAutoCheck(caps) && effective(Feature.COLOR_AUTO) != "false" -> "고정 색상 적용에는 주행 모드 연동 끄기 확인 필요"
            else -> null
        }
        item.copy(reason=item.reason ?: block)
    }.sortedBy { it.feature.ordinal }
}
@Serializable data class SessionEntry(val item: PlanItem, val attempted: Boolean = false, val status: String = "대기", val lastObserved: String? = null, val restoreStatus: String? = null)
@Serializable data class RideSession(val id: String, val profileName: String, val startedAt: Long, val entries: List<SessionEntry>, val closed: Boolean = false)
