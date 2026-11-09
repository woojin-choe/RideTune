package com.ridetune.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.util.Log
import com.ridetune.app.domain.CabinTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Pleos Vehicle SDK로 조명·스마트글라스·공조·창문을 맞춘다.
 *
 * - 값의 범위는 차량마다 달라서 항목마다 check…Capabilities로 먼저 범위와 지원 영역을 묻고, 지원하는 영역 전부에 적용한다.
 * - check…Capabilities는 응답이 올 때까지 호출한 스레드를 막는다(에뮬레이터에서 수 초). 메인 스레드에서 부르면 ANR이 나므로 IO에서 부른다.
 * - 지원 여부 조회가 실패하면 그 항목은 [Outcome.UNSUPPORTED], 적용이 실패하면 [Outcome.FAILED]로 남긴다.
 */
class PleosCabinController(context: Context) : CabinController {

    private val vehicle = Vehicle(context.applicationContext)
    private val started = AtomicBoolean(false)

    override fun start() {
        if (started.compareAndSet(false, true)) vehicle.initialize()
    }

    override fun stop() {
        if (started.compareAndSet(true, false)) vehicle.release()
    }

    override suspend fun apply(target: CabinTarget): ApplyReport = withContext(Dispatchers.IO) {
        val results = buildList {
            target.ambientBrightness?.let { add(ambientBrightness(it)) }
            target.ambientColor?.let { add(ambientColor(it)) }
            if (target.cabinLightOff) add(cabinLightOff())
            target.smartGlassDim?.let { add(smartGlass(it)) }
            target.temperatureC?.let { add(temperature(it)) }
            target.fanSpeed?.let { add(fanSpeed(it)) }
            target.recirculation?.let { add(recirculation(it)) }
            if (target.closeWindows) add(closeWindows())
        }
        ApplyReport(results)
    }

    private val light get() = vehicle.getLight()
    private val hvac get() = vehicle.getHVAC()
    private val window get() = vehicle.getWindow()

    private suspend fun ambientBrightness(fraction: Float) = step(CabinItem.AMBIENT_BRIGHTNESS) {
        val bounds = sdk { ok, err -> light.checkAmbientLightBrightnessCapabilities({ _, map -> ok(map) }, err) }
        bounds.forEach { (area, range) ->
            sdk<Unit> { ok, err -> light.setAmbientLightBrightness(area, range.scale(fraction), { _, _ -> ok(Unit) }, err) }
        }
    }

    private suspend fun ambientColor(argb: Int) = step(CabinItem.AMBIENT_COLOR) {
        val areas = sdk { ok, err -> light.checkAmbientLightBrightnessColorCapabilities({ _, list -> ok(list) }, err) }
        areas.forEach { area ->
            sdk<Unit> { ok, err -> light.setAmbientLightBrightnessColor(area, argb, { _, _ -> ok(Unit) }, err) }
        }
    }

    private suspend fun cabinLightOff() = step(CabinItem.CABIN_LIGHT) {
        val switches = sdk { ok, err -> light.checkCabinLightSwitchCapabilities({ list: List<LightSwitch> -> ok(list) }, err) }
        if (LightSwitch.OFF !in switches) throw UnsupportedOperationException("OFF 미지원")
        sdk<Unit> { ok, err -> light.setCabinLightSwitch(LightSwitch.OFF, { ok(Unit) }, err) }
    }

    // 스마트글라스 레벨이 클수록 어두워지는지는 문서에 없다. 에뮬레이터에서 확인 후 필요하면 뒤집는다.
    private suspend fun smartGlass(dim: Float) = step(CabinItem.SMART_GLASS) {
        val bounds = sdk { ok, err -> window.checkSmartGlassLevelCapabilities({ _, map -> ok(map) }, err) }
        bounds.forEach { (area, range) ->
            sdk<Unit> { ok, err -> window.setSmartGlassLevel(area, range.scale(dim), { _, _ -> ok(Unit) }, err) }
        }
    }

    private suspend fun temperature(celsius: Float) = step(CabinItem.TEMPERATURE) {
        val bounds = sdk { ok, err -> hvac.checkHvacTemperatureCapabilities({ map: Map<SeatArea, AreaBounds<Float>> -> ok(map) }, err) }
        bounds.forEach { (area, range) ->
            val value = celsius.coerceIn(range.minValue, range.maxValue)
            sdk<Unit> { ok, err -> hvac.setHvacTemperature(area, value, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, err) }
        }
    }

    private suspend fun fanSpeed(fraction: Float) = step(CabinItem.FAN_SPEED) {
        val bounds = sdk { ok, err -> hvac.checkHvacFanSpeedCapabilities({ map: Map<SeatArea, AreaBounds<Int>> -> ok(map) }, err) }
        bounds.forEach { (area, range) ->
            // 0이면 공조가 꺼질 수 있어 최소 1단은 남긴다.
            val value = range.scale(fraction).coerceAtLeast(maxOf(range.minValue, 1))
            sdk<Unit> { ok, err -> hvac.setHvacFanSpeed(area, value, { _, _ -> ok(Unit) }, err) }
        }
    }

    private suspend fun recirculation(on: Boolean) = step(CabinItem.RECIRCULATION) {
        val areas = sdk { ok, err -> hvac.checkHvacRecirculationCapabilities({ list: List<SeatArea> -> ok(list) }, err) }
        areas.forEach { area ->
            sdk<Unit> { ok, err -> hvac.setHvacRecirculation(area, on, { _, _ -> ok(Unit) }, err) }
        }
    }

    // 창문 위치의 최솟값을 "완전히 닫힘"으로 본다(에뮬레이터에서 확인 필요).
    private suspend fun closeWindows() = step(CabinItem.WINDOWS) {
        val bounds = sdk { ok, err -> window.checkWindowPositionCapabilities({ map: Map<WindowArea, AreaBounds<Int>> -> ok(map) }, err) }
        bounds.forEach { (area, range) ->
            sdk<Unit> { ok, err -> window.setWindowPosition(area, range.minValue, { _, _ -> ok(Unit) }, err) }
        }
    }

    /** 지원 여부 조회 단계에서 난 오류는 미지원, 그 뒤의 오류는 실패로 나눈다. */
    private suspend fun step(item: CabinItem, block: suspend StepScope.() -> Unit): ItemResult {
        val scope = StepScope()
        return try {
            scope.block()
            if (scope.applied == 0) ItemResult(item, Outcome.UNSUPPORTED, "지원 영역 없음")
            else ItemResult(item, Outcome.OK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$item 적용 실패", e)
            val outcome = if (scope.applied == 0 && scope.calls <= 1) Outcome.UNSUPPORTED else Outcome.FAILED
            ItemResult(item, outcome, e.message ?: e.javaClass.simpleName)
        }
    }

    private class StepScope {
        var calls = 0
        var applied = 0
    }

    /** SDK의 콜백 하나를 기다린다. 콜백이 두 번 와도 한 번만 받고, 오지 않으면 시간 초과로 실패한다. */
    private suspend fun <T> StepScope.sdk(call: (ok: (T) -> Unit, err: (Exception) -> Unit) -> Unit): T {
        val isApply = calls > 0
        calls++
        val result = withTimeout(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                call(
                    { value -> if (done.compareAndSet(false, true)) cont.resume(value) },
                    { e -> if (done.compareAndSet(false, true)) cont.resumeWithException(e) },
                )
            }
        }
        if (isApply) applied++
        return result
    }

    private fun AreaBounds<Int>.scale(fraction: Float): Int =
        (minValue + (maxValue - minValue) * fraction.coerceIn(0f, 1f)).roundToInt()

    private companion object {
        const val TAG = "RideTune/Cabin"
        const val TIMEOUT_MS = 5_000L
    }
}
