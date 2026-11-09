package com.ridetune.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinPlannerTest {

    @Test
    fun `민감한 곳이 없으면 아무것도 바꾸지 않는다`() {
        assertEquals(CabinTarget(), CabinPlanner.comfort(SensoryProfile()))
    }

    @Test
    fun `소리에 많이 민감하면 창문을 닫고 내기순환으로 바꾼다`() {
        val target = CabinPlanner.comfort(SensoryProfile(sound = Sensitivity.HIGH))
        assertTrue(target.closeWindows)
        assertEquals(true, target.recirculation)
        assertEquals(0.3f, target.fanSpeed)
        assertNull(target.ambientBrightness)
    }

    @Test
    fun `풍량은 소리와 바람 중 더 낮은 쪽을 따른다`() {
        val target = CabinPlanner.comfort(SensoryProfile(sound = Sensitivity.SOME, climate = Sensitivity.HIGH))
        assertEquals(0.25f, target.fanSpeed)
    }

    @Test
    fun `바람·온도에 민감할 때만 온도를 맞춘다`() {
        assertNull(CabinPlanner.comfort(SensoryProfile(light = Sensitivity.SOME)).temperatureC)
        assertEquals(24f, CabinPlanner.comfort(SensoryProfile(climate = Sensitivity.SOME, preferredTempC = 24f)).temperatureC)
    }

    @Test
    fun `빛에 조금 민감하면 실내등은 그대로 둔다`() {
        val target = CabinPlanner.comfort(SensoryProfile(light = Sensitivity.SOME))
        assertFalse(target.cabinLightOff)
        assertEquals(0.4f, target.ambientBrightness)
    }

    @Test
    fun `진정 모드는 프로필과 상관없이 모든 자극을 낮춘다`() {
        val target = CabinPlanner.calm(SensoryProfile(calmColor = CalmColor.GREEN))
        assertTrue(target.closeWindows)
        assertTrue(target.cabinLightOff)
        assertEquals(CalmColor.GREEN.argb, target.ambientColor)
        assertEquals(0.1f, target.ambientBrightness)
    }
}
