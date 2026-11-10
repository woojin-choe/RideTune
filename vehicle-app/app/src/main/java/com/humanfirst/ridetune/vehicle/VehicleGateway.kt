package com.humanfirst.ridetune.vehicle

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.*
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.*
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface VehicleGateway {
    val mode: String
    suspend fun capabilities(): List<Capability>
    suspend fun read(feature: Feature, area: String): String
    suspend fun write(feature: Feature, area: String, value: String)
    suspend fun parked(): Boolean
    fun close()
}
class PleosVehicleGateway(context: Context) : VehicleGateway {
    override val mode = "Pleos 실제 SDK"
    private val vehicle = Vehicle(context.applicationContext).apply { initialize() }
    private val hvac get() = vehicle.getHVAC()
    private val light get() = vehicle.getLight()
    private fun key(a: SeatArea) = a.values.map { it.name }.sorted().joinToString("|")
    private fun area(s: String) = SeatArea(*s.split('|').map { SeatValue.valueOf(it) }.toTypedArray())
    private suspend fun <T> call(block: ((T) -> Unit, (Exception) -> Unit) -> Unit): T = withTimeout(5000) {
        suspendCancellableCoroutine { c ->
            try { block({ if(c.isActive) c.resume(it) },{ if(c.isActive) c.resumeWithException(it) }) }
            catch(e: Exception) { if(c.isActive) c.resumeWithException(e) }
        }
    }
    override suspend fun capabilities(): List<Capability> = coroutineScope {
        Feature.entries.map { f -> async {
            try { capability(f) } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException) throw e; listOf(Capability(f,"GLOBAL","NONE",error="${e.javaClass.simpleName}: ${e.message ?: "조회 실패"}")) }
        } }.awaitAll().flatten()
    }
    private suspend fun capability(f: Feature): List<Capability> {
        fun bools(c: CapabilityConfig, xs: List<SeatArea>) = xs.map { Capability(f,key(it),c.accessType.name,options=listOf("false","true")) }
        return when(f) {
            Feature.TEMPERATURE -> call { ok, fail -> hvac.checkHvacTemperatureCapabilities({ c,m,t -> ok(m.map { (a,b) -> Capability(f,key(a),c.accessType.name,b.minValue?.toDouble(),b.maxValue?.toDouble(),t?.incrementCelsius?.toDouble()) }) },fail) }
            Feature.FAN -> call { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ c,m -> ok(m.map { (a,b) -> Capability(f,key(a),c.accessType.name,b.minValue?.toDouble(),b.maxValue?.toDouble(),1.0) }) },fail) }
            Feature.DIRECTION -> call { ok, fail -> hvac.checkHvacFanDirectionCapabilities({ c,m -> ok(m.map { (a,v) -> Capability(f,key(a),c.accessType.name,options=v.map { it.name }) }) },fail) }
            Feature.POWER -> call { ok,fail -> hvac.checkHvacPowerCapabilities({ c,a -> ok(bools(c,a)) },fail) }
            Feature.AUTO -> call { ok,fail -> hvac.checkHvacAutoCapabilities({ c,a -> ok(bools(c,a)) },fail) }
            Feature.AC -> call { ok,fail -> hvac.checkHvacAcCapabilities({ c,a -> ok(bools(c,a)) },fail) }
            Feature.BRIGHTNESS -> call { ok,fail -> light.checkAmbientLightBrightnessCapabilities({ c,m -> ok(m.map { (a,b) -> Capability(f,key(a),c.accessType.name,b.minValue?.toDouble(),b.maxValue?.toDouble(),1.0) }) },fail) }
            Feature.COLOR -> call { ok,fail -> light.checkAmbientLightBrightnessColorCapabilities({ c,a -> ok(a.map { Capability(f,key(it),c.accessType.name) }) },fail) }
            Feature.LIGHT -> call { ok,fail -> light.checkAmbientLightSwitchCapabilities({ c,m -> ok(m.map { (a,v) -> Capability(f,key(a),c.accessType.name,options=v.map { it.name }) }) },fail) }
            Feature.COLOR_AUTO -> call { ok,fail -> light.checkAmbientColorAutoByDriveModeEnabledCapabilities({ c -> ok(listOf(Capability(f,"GLOBAL",c.accessType.name,options=listOf("false","true")))) },fail) }
        }
    }
    override suspend fun read(feature: Feature, area: String): String {
        fun <T> nonNull(ok: (String)->Unit, fail:(Exception)->Unit, render:(T)->String = {it.toString()}): (T?)->Unit = { if(it == null) fail(IllegalStateException("차량값 없음")) else ok(render(it)) }
        return call { ok, fail -> when(feature) {
            Feature.TEMPERATURE -> hvac.getHvacTemperature(area(area),TemperatureUnit.CELSIUS,nonNull(ok,fail),fail)
            Feature.FAN -> hvac.getHvacFanSpeed(area(area),nonNull(ok,fail),fail)
            Feature.DIRECTION -> hvac.getHvacFanDirection(area(area),nonNull(ok,fail) { it.name },fail)
            Feature.POWER -> hvac.getHvacPowerState(area(area),nonNull(ok,fail),fail)
            Feature.AUTO -> hvac.getHvacAutoState(area(area),nonNull(ok,fail),fail)
            Feature.AC -> hvac.getHvacAcState(area(area),nonNull(ok,fail),fail)
            Feature.BRIGHTNESS -> light.getAmbientLightBrightness(area(area),nonNull(ok,fail),fail)
            Feature.COLOR -> light.getAmbientLightBrightnessColor(area(area),nonNull(ok,fail) { "#%06X".format(it and 0xFFFFFF) },fail)
            Feature.LIGHT -> light.getAmbientLightSwitch(area(area),nonNull(ok,fail) {it.name},fail)
            Feature.COLOR_AUTO -> light.getAmbientColorAutoByDriveModeEnabled(nonNull(ok,fail),fail)
        } }
    }
    override suspend fun write(feature: Feature, area: String, value: String) {
        call<Unit> { ok,fail -> when(feature) {
            Feature.TEMPERATURE -> hvac.setHvacTemperature(area(area),value.toFloat(),TemperatureUnit.CELSIUS,{_,_->ok(Unit)},fail)
            Feature.FAN -> hvac.setHvacFanSpeed(area(area),value.toInt(),{_,_->ok(Unit)},fail)
            Feature.DIRECTION -> hvac.setHvacFanDirection(area(area),FanDirectionType.valueOf(value),{_,_->ok(Unit)},fail)
            Feature.POWER -> hvac.setHvacPower(area(area),value.toBooleanStrict(),{_,_->ok(Unit)},fail)
            Feature.AUTO -> hvac.setHvacAuto(area(area),value.toBooleanStrict(),{_,_->ok(Unit)},fail)
            Feature.AC -> hvac.setHvacAc(area(area),value.toBooleanStrict(),{_,_->ok(Unit)},fail)
            Feature.BRIGHTNESS -> light.setAmbientLightBrightness(area(area),value.toInt(),{_,_->ok(Unit)},fail)
            Feature.COLOR -> light.setAmbientLightBrightnessColor(area(area),(0xFF000000L or value.drop(1).toLong(16)).toInt(),{_,_->ok(Unit)},fail)
            Feature.LIGHT -> light.setAmbientLightSwitch(area(area),LightSwitch.valueOf(value),{_,_->ok(Unit)},fail)
            Feature.COLOR_AUTO -> light.setAmbientColorAutoByDriveModeEnabled(value.toBooleanStrict(),{ok(Unit)},fail)
        } }
    }
    @Suppress("DEPRECATION")
    override suspend fun parked(): Boolean = call { ok,fail -> vehicle.getGear().getCurrentGearSelectionState({ok(it == VehicleGear.GEAR_PARK)},fail) }
    override fun close() = vehicle.release()
}

class FakeVehicleGateway : VehicleGateway {
    override val mode = "모의 차량"
    var isParked = true
    val state = mutableMapOf<String,String>()
    var caps = listOf(Capability(Feature.TEMPERATURE,"ROW_1_RIGHT","READ_WRITE",16.0,30.0,0.5))
    var writes = 0
    var failWrites = false
    override suspend fun capabilities() = caps
    override suspend fun read(feature: Feature, area: String) = state["${feature.name}:$area"] ?: error("모의값 없음")
    override suspend fun write(feature: Feature, area: String, value: String) { writes++; if(failWrites) error("모의 쓰기 실패"); state["${feature.name}:$area"] = value }
    override suspend fun parked() = isParked
    override fun close() {}
}
