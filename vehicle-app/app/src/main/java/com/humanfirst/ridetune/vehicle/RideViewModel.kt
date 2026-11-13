package com.humanfirst.ridetune.vehicle

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

data class RideState(
    val profiles: List<PassengerProfile> = emptyList(), val selected: PassengerProfile? = null,
    val screen: String = "home", val busy: Boolean = false, val parked: Boolean? = null,
    val capabilities: List<Capability> = emptyList(), val current: Map<String,String> = emptyMap(),
    val readErrors: Map<String,String> = emptyMap(), val plan: List<PlanItem> = emptyList(),
    val session: RideSession? = null, val message: String? = null, val storageError: Boolean = false,
    val connected: Boolean = false, val restorePreview: Map<String,String> = emptyMap(),
    val companionTurns: List<CompanionTurn> = emptyList(),
    val repeatCount: Int = 0,
    val voiceEnabled: Boolean = true,
    val breathingPhase: String? = null
)

class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val gateway: VehicleGateway = PleosVehicleGateway(app)
    private val mutable = MutableStateFlow(RideState())
    val state = mutable.asStateFlow()
    private val engine = SessionEngine(gateway) { s -> store.saveSession(s); mutable.update {it.copy(session=s)} }
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        try { mutable.update { it.copy(profiles=store.profiles(),session=store.session()) } }
        catch(e: Exception) { mutable.update {it.copy(storageError=true,message="저장 데이터 확인 실패: ${e.message}")} }
        runCatching {
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.KOREAN
                    tts?.setSpeechRate(0.94f)
                    ttsReady = true
                }
            }
        }
        viewModelScope.launch {
            delay(1500); refreshNow()
            while(isActive) {
                val p = try { gateway.parked() } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; null }
                mutable.update { it.copy(parked=p) }; delay(2000)
            }
        }
    }

    private fun speak(text: String) {
        if (!mutable.value.voiceEnabled || !ttsReady) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ridetune-companion") }
    }

    fun toggleVoice() {
        val next = !mutable.value.voiceEnabled
        if (!next) runCatching { tts?.stop() }
        mutable.update { it.copy(voiceEnabled = next) }
    }

    private fun job(block: suspend ()->Unit) {
        if(mutable.value.busy) return
        mutable.update {it.copy(busy=true,message=null)}
        viewModelScope.launch {
            try { block() } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; mutable.update {it.copy(message=e.message ?: "작업 실패")} }
            finally {mutable.update {it.copy(busy=false)}}
        }
    }
    private suspend fun requirePark() { check(gateway.parked()) { "P단에서 다시 시도해주세요" }; check(!mutable.value.storageError) { "저장 데이터 오류를 먼저 해결해주세요" } }
    private suspend fun refreshNow() {
        val caps = gateway.capabilities(); val values = mutableMapOf<String,String>(); val errors = mutableMapOf<String,String>()
        for(c in caps.filter {it.readable}) {
            val key = "${c.feature.name}:${c.area}"
            try { values[key] = gateway.read(c.feature,c.area) } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; errors[key] = e.message ?: "읽기 실패" }
        }
        val parked = try { gateway.parked() } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; null }
        mutable.update {it.copy(capabilities=caps,current=values,readErrors=errors,connected=values.isNotEmpty(),parked=parked)}
    }
    fun refresh() = job { refreshNow() }
    fun dismiss() {mutable.update {it.copy(message=null)}}
    fun home() {if(!mutable.value.busy) { runCatching { tts?.stop() }; mutable.update {it.copy(screen="home",plan=emptyList(),breathingPhase=null)} }}
    fun diagnostics() {if(!mutable.value.busy) mutable.update {it.copy(screen="diagnostics")}}
    fun edit(p: PassengerProfile) = job {requirePark(); mutable.update {it.copy(selected=p,screen="edit")} }
    fun showReceivedProfile(profile: PassengerProfile) {
        mutable.update { it.copy(selected=profile,screen="received",plan=emptyList(),message=null) }
    }
    fun save(p: PassengerProfile) = job {
        requirePark(); ProfileCodec.validate(p)
        val ps = mutable.value.profiles.toMutableList(); val index = ps.indexOfFirst {it.profileId == p.profileId}
        if(index < 0) {check(ps.size < 20) {"최대 20개 프로필"}; ps.add(p)} else ps[index] = p
        withContext(Dispatchers.IO) { store.saveProfiles(ps) }
        mutable.update {it.copy(profiles=ps,selected=p,screen="home",message="프로필을 기기에 저장했습니다")}
    }
    fun preview(p: PassengerProfile) = job {
        requirePark(); check(mutable.value.session?.closed != false) {"진행 중인 이용을 먼저 종료해주세요"}
        refreshNow(); val s = mutable.value
        mutable.update {it.copy(selected=p,plan=buildPlan(p,s.capabilities,s.current),screen="preview")}
    }
    fun apply() = job {
        requirePark(); check(mutable.value.session?.closed != false) {"이미 진행 중인 이용이 있습니다"}
        val s = mutable.value
        mutable.update {it.copy(screen="session")}
        engine.apply(s.selected!!.displayName,s.plan)
        refreshNow()
    }
    fun openCompanion(profile: PassengerProfile? = null) = job {
        val target = profile ?: mutable.value.selected ?: mutable.value.profiles.firstOrNull() ?: ProfileCodec.examples().first()
        val turn = AiCompanionEngine.openingTurn(target, mutable.value.parked, mutable.value.current)
        mutable.update {
            it.copy(
                selected = target,
                screen = "companion",
                repeatCount = 0,
                breathingPhase = null,
                companionTurns = listOf(turn)
            )
        }
        speak(turn.aiReply)
    }
    fun askRepeatQuestion() {
        val s = mutable.value
        val p = s.selected ?: s.profiles.firstOrNull() ?: return
        val nextCount = s.repeatCount + 1
        val turn = AiCompanionEngine.repeatQuestionTurn(p, nextCount, s.parked)
        mutable.update {
            it.copy(
                repeatCount = nextCount,
                breathingPhase = null,
                companionTurns = listOf(turn) + it.companionTurns.take(8)
            )
        }
        speak(turn.aiReply)
    }
    fun explainWindowWeather(weather: String) = job {
        val s = mutable.value
        val base = s.selected ?: s.profiles.firstOrNull() ?: return@job
        val updatedProfile = base.copy(weatherScene = weather)
        val (turn, newPrefs) = AiCompanionEngine.windowWeatherTurn(updatedProfile, weather, s.parked)
        val syncedProfile = updatedProfile.copy(preferences = newPrefs)
        mutable.update {
            it.copy(
                selected = syncedProfile,
                breathingPhase = null,
                companionTurns = listOf(turn) + it.companionTurns.take(8)
            )
        }
        speak(turn.aiReply)
        applyLiveComfort(syncedProfile)
    }
    fun triggerSensorySos(kind: String) = job {
        val s = mutable.value
        val base = s.selected ?: s.profiles.firstOrNull() ?: return@job
        val (turn, newPrefs) = AiCompanionEngine.sensorySosTurn(base, kind)
        val syncedProfile = base.copy(preferences = newPrefs)
        mutable.update {
            it.copy(
                selected = syncedProfile,
                companionTurns = listOf(turn) + it.companionTurns.take(8)
            )
        }
        speak(turn.aiReply)
        if (kind == "BREATHE") {
            runBreathingSequence(syncedProfile)
        } else {
            mutable.update { it.copy(breathingPhase = null) }
            applyLiveComfort(syncedProfile)
        }
    }
    private suspend fun runBreathingSequence(profile: PassengerProfile) {
        val steps = listOf(
            "들숨 (4초) · 파란 바다 불빛이 은은하게 밝아져요 (45%)" to 45,
            "머금기 (2초) · 파도 위에서 편안하게 숨을 고르고 있어요" to 35,
            "날숨 (4초) · 불빛이 어두워질 때 후— 내쉬어요 (15%)" to 15,
            "안정 완료 · 바다님의 편안한 밝기(20%)로 고정되었어요" to 20
        )
        for ((label, brightness) in steps) {
            mutable.update { it.copy(breathingPhase = label) }
            val stepProfile = profile.copy(preferences = profile.preferences.copy(ambientEnabled = true, ambientBrightnessPercent = brightness, ambientColorHex = "#5588CC", ambientColorAuto = false))
            applyLiveComfort(stepProfile, refreshAfter = false)
            delay(1800)
        }
        refreshNow()
    }
    private suspend fun applyLiveComfort(profile: PassengerProfile, refreshAfter: Boolean = true) {
        val isParked = try { gateway.parked() } catch (_: Exception) { false }
        if (!isParked) {
            mutable.update { it.copy(message = "주행 중에는 AI 음성/화면 진정 안내를 우선 제공하며, 차량 하드웨어 변경은 P단에서 즉시 적용됩니다.") }
            return
        }
        refreshNow()
        val s = mutable.value
        val plan = buildPlan(profile, s.capabilities, s.current)
        if (s.session == null || s.session.closed) {
            if (plan.any { it.actionable }) {
                runCatching { engine.apply(profile.displayName, plan) }
            }
        } else {
            for (item in plan.filter { it.actionable && it.target != null }) {
                runCatching { gateway.write(item.feature, item.area, item.target!!) }
            }
            val updatedEntries = s.session.entries.map { entry ->
                val match = plan.find { it.feature == entry.item.feature && it.area == entry.item.area && it.target != null }
                if (match != null) entry.copy(item = entry.item.copy(target = match.target), attempted = true, status = "적용 확인", lastObserved = match.target)
                else entry
            }
            val updatedSession = s.session.copy(entries = updatedEntries)
            store.saveSession(updatedSession)
            mutable.update { it.copy(session = updatedSession) }
        }
        if (refreshAfter) refreshNow()
    }
    fun session() = job {
        val s = mutable.value.session ?: error("진행 중인 이용 없음")
        mutable.update {it.copy(screen="session")}
        val latest = engine.observe(s); store.saveSession(latest); mutable.update {it.copy(session=latest)}
    }
    fun retry() = job { requirePark(); engine.retry(mutable.value.session ?: error("이용 기록 없음")); refreshNow() }
    fun restorePreview() = job {
        requirePark(); val session = mutable.value.session ?: error("이용 기록 없음")
        val values = mutableMapOf<String,String>()
        for(e in session.entries.filter {it.attempted}) {
            values["${e.item.feature}:${e.item.area}"] = try { gateway.read(e.item.feature,e.item.area) } catch(ex: Exception) {if(ex is CancellationException && ex !is TimeoutCancellationException) throw ex; "확인 불가"}
        }
        mutable.update {it.copy(screen="restore",restorePreview=values)}
    }
    fun restore(keepConflicts: Boolean) = job {
        requirePark(); val s = mutable.value.session ?: error("이용 기록 없음")
        val result = engine.restore(s,keepConflicts)
        refreshNow()
        mutable.update {it.copy(screen=if(result.closed) "home" else "session",message=if(result.closed) "이용이 종료됐습니다. 복원 결과를 확인할 수 있습니다." else "복원 보류 항목을 확인해주세요")}
    }
    fun importProfile(text: String) {
        try { val p = ProfileCodec.decode(text); edit(p) } catch(e: Exception) {mutable.update {it.copy(message=e.message)}}
    }
    override fun onCleared() {
        runCatching { tts?.stop(); tts?.shutdown() }
        gateway.close()
        super.onCleared()
    }
}
