package com.humanfirst.ridetune.vehicle

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

class SessionEngine(private val gateway: VehicleGateway, private val persist: (RideSession)->Unit) {
    private val mutex = Mutex()
    private suspend fun <T> exclusively(block: suspend ()->T): T {
        check(mutex.tryLock()) { "다른 적용 작업이 진행 중입니다" }
        try { return block() } finally { mutex.unlock() }
    }
    private suspend fun requirePark() { check(gateway.parked()) { "P단 확인 후에만 설정을 변경할 수 있습니다" } }
    private suspend fun valid(item: PlanItem, value: String) {
        val c = gateway.capabilities().find { it.feature == item.feature && it.area == item.area }
        checkNotNull(c) { "지원 기능을 찾을 수 없습니다" }
        check(c.writable) { "기능 지원 또는 권한이 변경됐습니다" }
        if(c.options.isNotEmpty()) check(value in c.options) { "지원하지 않는 값" }
        if(c.min != null && c.max != null) check(value.toDouble() in c.min..c.max) { "허용 범위 밖" }
    }
    private suspend fun dependencies(item: PlanItem) {
        suspend fun expected(f: Feature, value: String) {
            val c = gateway.capabilities().find { it.feature == f && (it.area == "GLOBAL" || item.area.split('|').any { seat -> seat in it.area.split('|') }) }
            check(c?.readable == true && gateway.read(f,c.area) == value) { "${f.label} 상태를 확인하세요" }
        }
        if(item.feature in listOf(Feature.TEMPERATURE,Feature.FAN,Feature.DIRECTION,Feature.AC,Feature.AUTO)) expected(Feature.POWER,"true")
        if(item.feature in listOf(Feature.FAN,Feature.DIRECTION)) expected(Feature.AUTO,"false")
        if(item.feature == Feature.COLOR && requiresColorAutoCheck(gateway.capabilities())) expected(Feature.COLOR_AUTO,"false")
    }
    private suspend fun setVerified(item: PlanItem, value: String): String {
        var writeError: Exception? = null
        try { gateway.write(item.feature,item.area,value) } catch(e: Exception) {
            if(e is CancellationException && e !is TimeoutCancellationException) throw e
            writeError = e
        }
        repeat(5) {
            delay(200)
            val observed = try { gateway.read(item.feature,item.area) } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; null }
            if(sameValue(item.feature,observed,value)) return observed!!
        }
        throw IllegalStateException("적용 확인 안 됨${writeError?.message?.let { ": $it" } ?: ""}")
    }
    suspend fun apply(name: String, plan: List<PlanItem>): RideSession = exclusively {
        requirePark()
        check(plan.any { it.actionable }) { "변경할 항목이 없습니다" }
        var s = RideSession(UUID.randomUUID().toString(),name,System.currentTimeMillis(),plan.map { SessionEntry(it,status=when { it.reason != null -> "제외: ${it.reason}"; !it.actionable -> "이미 같은 상태"; else -> "대기" }) })
        persist(s)
        for(i in s.entries.indices) {
            val entry = s.entries[i]; val item = entry.item
            if(!item.actionable) continue
            try {
                requirePark(); valid(item,item.target!!); dependencies(item)
                check(sameValue(item.feature,gateway.read(item.feature,item.area),item.before)) { "미리보기 이후 설정이 바뀌었습니다. 재확인 필요" }
                s = s.copy(entries=s.entries.toMutableList().also { it[i] = entry.copy(attempted=true,status="적용 중") }); persist(s)
                val actual = setVerified(item,item.target)
                s = s.copy(entries=s.entries.toMutableList().also { it[i] = it[i].copy(status="적용 확인",lastObserved=actual) }); persist(s)
            } catch(e: Exception) {
                if(e is CancellationException && e !is TimeoutCancellationException) throw e
                s = s.copy(entries=s.entries.toMutableList().also { it[i] = it[i].copy(status="실패: ${e.message}") }); persist(s)
            }
        }
        // Recheck the whole result after interacting modes have settled.
        s = observe(s); persist(s); s
    }
    suspend fun retry(s: RideSession): RideSession = exclusively {
        requirePark()
        check(!s.closed) { "종료된 이용입니다" }
        var result = s
        for(i in result.entries.indices) {
            val e = result.entries[i]; val item = e.item
            if(!item.actionable || e.restoreStatus != null) continue
            try {
                requirePark(); valid(item,item.target!!)
                val now = gateway.read(item.feature,item.area)
                if(sameValue(item.feature,now,item.target)) continue
                check(sameValue(item.feature,now,item.before)) { "현재값이 변경돼 재시도 보류" }
                dependencies(item)
                result = result.copy(entries=result.entries.toMutableList().also { it[i]=e.copy(attempted=true,status="재시도 중") }); persist(result)
                val actual = setVerified(item,item.target)
                result = result.copy(entries=result.entries.toMutableList().also {it[i]=it[i].copy(status="적용 확인",lastObserved=actual)}); persist(result)
            } catch(ex: Exception) {
                if(ex is CancellationException && ex !is TimeoutCancellationException) throw ex
                result=result.copy(entries=result.entries.toMutableList().also {it[i]=it[i].copy(status="재시도 보류: ${ex.message}")});persist(result)
            }
        }
        result=observe(result);persist(result);result
    }
    suspend fun observe(s: RideSession): RideSession = s.copy(entries=s.entries.map { e ->
        if(!e.attempted || e.restoreStatus == "복원 확인" || e.restoreStatus == "현재값 유지") e else {
            try { val v = gateway.read(e.item.feature,e.item.area)
                e.copy(lastObserved=v,status=if(sameValue(e.item.feature,v,e.item.target)) "적용 확인" else "현재값이 목표와 다름")
            } catch(ex: Exception) { if(ex is CancellationException && ex !is TimeoutCancellationException) throw ex; e.copy(status="현재값 확인 불가",lastObserved=null) }
        }
    })
    suspend fun restore(s: RideSession, keepConflicts: Boolean): RideSession = exclusively {
        requirePark()
        var result = s
        // Reverse order restores manual values before AUTO/POWER settings.
        for(i in result.entries.indices.reversed()) {
            val e = result.entries[i]; val item = e.item
            if(!e.attempted || e.restoreStatus in listOf("복원 확인","현재값 유지")) continue
            try {
                requirePark(); val current = gateway.read(item.feature,item.area)
                val conflict = !sameValue(item.feature,current,item.target) && !sameValue(item.feature,current,item.before)
                if(conflict && keepConflicts) {
                    result = result.copy(entries=result.entries.toMutableList().also { it[i] = e.copy(restoreStatus="현재값 유지") }); persist(result); continue
                }
                check(!conflict) { "이용 중 값이 변경됨 · 현재값 유지 또는 수동 확인 필요" }
                valid(item,item.before!!)
                result = result.copy(entries=result.entries.toMutableList().also { it[i] = e.copy(restoreStatus="복원 중") }); persist(result)
                val v = if(sameValue(item.feature,current,item.before)) current else setVerified(item,item.before)
                result = result.copy(entries=result.entries.toMutableList().also { it[i] = it[i].copy(restoreStatus="복원 확인",lastObserved=v) }); persist(result)
            } catch(ex: Exception) {
                if(ex is CancellationException && ex !is TimeoutCancellationException) throw ex
                result = result.copy(entries=result.entries.toMutableList().also { it[i] = e.copy(restoreStatus="복원 보류: ${ex.message}") }); persist(result)
            }
        }
        result = result.copy(entries=result.entries.map { e ->
            if(e.restoreStatus != "복원 확인") e else try {
                val v=gateway.read(e.item.feature,e.item.area)
                e.copy(lastObserved=v,restoreStatus=if(sameValue(e.item.feature,v,e.item.before)) "복원 확인" else "복원 보류: 다른 모드 변경 후 재확인 필요")
            } catch(ex: Exception) { if(ex is CancellationException && ex !is TimeoutCancellationException) throw ex; e.copy(restoreStatus="복원 보류: 최종값 확인 불가") }
        })
        result = result.copy(closed=result.entries.all { !it.attempted || it.restoreStatus in listOf("복원 확인","현재값 유지") })
        persist(result); result
    }
}
