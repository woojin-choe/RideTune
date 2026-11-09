package com.ridetune.app.domain

/**
 * 차 안을 이렇게 맞추고 싶다는 목표. 차량마다 값의 범위가 달라서 밝기·풍량·스마트글라스는 0..1 비율로 두고,
 * 실제 값으로 바꾸는 일은 [com.ridetune.app.data.vehicle.CabinController]가 한다. null이면 건드리지 않는다.
 */
data class CabinTarget(
    val ambientBrightness: Float? = null,
    val ambientColor: Int? = null,
    val cabinLightOff: Boolean = false,
    /** 스마트글라스를 얼마나 어둡게 할지(0 = 투명, 1 = 가장 어둡게) */
    val smartGlassDim: Float? = null,
    val temperatureC: Float? = null,
    val fanSpeed: Float? = null,
    val recirculation: Boolean? = null,
    val closeWindows: Boolean = false,
)
