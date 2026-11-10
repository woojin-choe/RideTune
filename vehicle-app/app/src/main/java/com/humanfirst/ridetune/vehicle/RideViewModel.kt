package com.humanfirst.ridetune.vehicle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString

 data class RideState(
    val profiles: List<PassengerProfile> = emptyList(), val selected: PassengerProfile? = null,
    val screen: String = "home", val busy: Boolean = false, val parked: Boolean? = null,
    val capabilities: List<Capability> = emptyList(), val current: Map<String,String> = emptyMap(),
    val readErrors: Map<String,String> = emptyMap(), val plan: List<PlanItem> = emptyList(),
    val session: RideSession? = null, val message: String? = null, val storageError: Boolean = false,
    val connected: Boolean = false, val restorePreview: Map<String,String> = emptyMap()
)
class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val gateway: VehicleGateway = PleosVehicleGateway(app)
    private val mutable = MutableStateFlow(RideState())
    val state = mutable.asStateFlow()
    private val engine = SessionEngine(gateway) { s -> store.saveSession(s); mutable.update {it.copy(session=s)} }
    init {
        try { mutable.update { it.copy(profiles=store.profiles(),session=store.session()) } }
        catch(e: Exception) { mutable.update {it.copy(storageError=true,message="저장 데이터 확인 실패: ${e.message}")} }
        viewModelScope.launch {
            delay(1500); refreshNow()
            while(isActive) {
                val p = try { gateway.parked() } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; null }
                mutable.update { it.copy(parked=p) }; delay(2000)
            }
        }
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
    fun home() {if(!mutable.value.busy) mutable.update {it.copy(screen="home",plan=emptyList())}}
    fun diagnostics() {if(!mutable.value.busy) mutable.update {it.copy(screen="diagnostics")}}
    fun edit(p: PassengerProfile) = job {requirePark(); check(mutable.value.session?.closed != false) {"진행 중인 이용을 먼저 종료해주세요"}; mutable.update {it.copy(selected=p,screen="edit")} }
    fun save(p: PassengerProfile) = job {
        requirePark(); ProfileCodec.validate(p)
        val ps = mutable.value.profiles.toMutableList(); val index = ps.indexOfFirst {it.profileId == p.profileId}
        if(index < 0) {check(ps.size < 20) {"최대 20개 프로필"}; ps.add(p)} else ps[index] = p
        withContext(Dispatchers.IO) { store.saveProfiles(ps) }
        mutable.update {it.copy(profiles=ps,screen="home",message="프로필을 기기에 저장했습니다")}
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
    override fun onCleared() { gateway.close(); super.onCleared() }
}
