package com.ridetune.app.data.vehicle

import com.ridetune.app.domain.CabinTarget

/** 차 안 설정을 실제 차량에 적용한다. 항목 하나가 실패해도 나머지는 계속 적용한다. */
interface CabinController {
    fun start()
    fun stop()
    suspend fun apply(target: CabinTarget): ApplyReport
}

enum class CabinItem { AMBIENT_BRIGHTNESS, AMBIENT_COLOR, CABIN_LIGHT, SMART_GLASS, TEMPERATURE, FAN_SPEED, RECIRCULATION, WINDOWS }

enum class Outcome { OK, UNSUPPORTED, FAILED }

data class ItemResult(val item: CabinItem, val outcome: Outcome, val detail: String? = null)

data class ApplyReport(val results: List<ItemResult>) {
    val okCount get() = results.count { it.outcome == Outcome.OK }
}
