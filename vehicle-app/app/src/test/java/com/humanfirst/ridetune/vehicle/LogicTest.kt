package com.humanfirst.ridetune.vehicle

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class LogicTest {
    private val area="ROW_1_RIGHT"
    private fun profile(p: Preferences) = PassengerProfile(profileId="test",displayName="테스트",preferences=p)
    private val caps=listOf(Capability(Feature.TEMPERATURE,area,"READ_WRITE",15.0,30.0,0.5),Capability(Feature.POWER,area,"READ_WRITE",options=listOf("false","true")),Capability(Feature.AUTO,area,"READ_WRITE",options=listOf("false","true")),Capability(Feature.FAN,area,"READ_WRITE",1.0,8.0,1.0))
    private val current=mapOf("TEMPERATURE:$area" to "24.0","POWER:$area" to "true","AUTO:$area" to "false","FAN:$area" to "3")
    @Test fun nullIsNotOff() {assertTrue(Preferences().values().isEmpty());assertEquals("OFF",Preferences(ambientEnabled=false).values()[Feature.LIGHT])}
    @Test fun rejectsBadJsonAndEnums() {
        listOf("{}","{\"schemaVersion\":99}","x".repeat(17000)).forEach {assertTrue(runCatching {ProfileCodec.decode(it)}.isFailure)}
        assertTrue(runCatching {ProfileCodec.validate(profile(Preferences(fanPreference="LOUD")))}.isFailure)
        assertTrue(runCatching {ProfileCodec.validate(profile(Preferences(auto=true,fanPreference="LOW")))}.isFailure)
    }
    @Test fun rejectsOutOfRangeAndStep() {
        for(v in listOf(14f,31f,22.3f)) assertNotNull(buildPlan(profile(Preferences(temperatureCelsius=v)),caps,current).first().reason)
    }
    @Test fun mapsValidFanAndRejectsUnsupportedOff() {
        assertEquals("1",buildPlan(profile(Preferences(fanPreference="LOW")),caps,current).first().target)
        assertNotNull(buildPlan(profile(Preferences(fanPreference="OFF")),caps,current).first().reason)
    }
    @Test fun noSeatFallbackAndNoWriteOnly() {
        assertNotNull(buildPlan(profile(Preferences(temperatureCelsius=22f)).copy(preferredSeat="ROW_2_RIGHT"),caps,current).first().reason)
        assertNotNull(buildPlan(profile(Preferences(temperatureCelsius=22f)),caps.map{it.copy(access="WRITE")},current).first().reason)
    }
    @Test fun sharedAreaIsVisible() {
        val a="ROW_1_LEFT|ROW_1_RIGHT"
        val plan=buildPlan(profile(Preferences(ambientEnabled=true)),listOf(Capability(Feature.LIGHT,a,"READ_WRITE",options=listOf("ON","OFF"))),mapOf("LIGHT:$a" to "OFF"))
        assertEquals(a,plan.first().area);assertTrue(plan.first().note.isNotEmpty())
    }
    @Test fun colorAutoErrorsAndOrdering() {
        assertTrue(requiresColorAutoCheck(listOf(Capability(Feature.COLOR_AUTO,"GLOBAL","NONE",error="permission denied"))))
        assertFalse(requiresColorAutoCheck(listOf(Capability(Feature.COLOR_AUTO,"GLOBAL","NONE",error="ERROR_NOT_SUPPORTED_PROPERTY"))))
        val cs = listOf(Capability(Feature.COLOR_AUTO,"GLOBAL","READ_WRITE",options=listOf("false","true")), Capability(Feature.COLOR,area,"READ_WRITE"))
        val plan=buildPlan(profile(Preferences(ambientColorHex="#5588CC",ambientColorAuto=false)),cs,mapOf("COLOR_AUTO:GLOBAL" to "true","COLOR:$area" to "#FFFFFF"))
        assertEquals(listOf(Feature.COLOR_AUTO,Feature.COLOR),plan.map {it.feature})
        assertTrue(plan.all {it.actionable})
    }
    private fun fake()=FakeVehicleGateway().also { it.caps=caps;it.state.putAll(current) }
    @Test fun applyRestoreAndCrashJournal()=runTest {
        val g=fake();val records=mutableListOf<RideSession>();val engine=SessionEngine(g){records.add(it)}
        val s=engine.apply("test",buildPlan(profile(Preferences(temperatureCelsius=22f)),caps,current))
        assertEquals("적용 확인",s.entries.first().status)
        assertTrue(records.any {it.entries.first().attempted && it.entries.first().status=="적용 중"})
        val reloaded=profileJson.decodeFromString<RideSession>(kotlinx.serialization.json.Json.encodeToString(RideSession.serializer(),s))
        assertTrue(engine.restore(reloaded,false).closed);assertEquals("24.0",g.state["TEMPERATURE:$area"])
    }
    @Test fun neverOverwriteExternalChangeOnRestore()=runTest {
        val g=fake();val engine=SessionEngine(g){}
        val s=engine.apply("test",buildPlan(profile(Preferences(temperatureCelsius=22f)),caps,current))
        g.state["TEMPERATURE:$area"]="26.0"
        assertFalse(engine.restore(s,false).closed);assertEquals("26.0",g.state["TEMPERATURE:$area"])
        assertTrue(engine.restore(s,true).closed);assertEquals("26.0",g.state["TEMPERATURE:$area"])
    }
    @Test fun failureNotSuccessAndCanRetry()=runTest {
        val g=fake();g.failWrites=true;val engine=SessionEngine(g){}
        val s=engine.apply("test",buildPlan(profile(Preferences(temperatureCelsius=22f)),caps,current))
        assertNotEquals("적용 확인",s.entries.first().status)
        g.failWrites=false;assertEquals("적용 확인",engine.retry(s).entries.first().status)
    }
    @Test fun cannotApplyWhenDriving()=runTest {
        val g=fake();g.isParked=false;val engine=SessionEngine(g){}
        assertTrue(runCatching {engine.apply("test",buildPlan(profile(Preferences(temperatureCelsius=22f)),caps,current))}.isFailure)
        assertEquals(0,g.writes)
    }
    @Test fun stalePreviewDoesNotOverwrite()=runTest {
        val g=fake();g.state["TEMPERATURE:$area"]="26.0"
        val s=SessionEngine(g){}.apply("test",buildPlan(profile(Preferences(temperatureCelsius=22f)),caps,current))
        assertEquals(0,g.writes);assertFalse(s.entries.first().attempted)
    }
    @Test fun rejectsConcurrentApply()=runTest {
        val g=fake();val engine=SessionEngine(g){};val plan=buildPlan(profile(Preferences(temperatureCelsius=22f)),caps,current)
        val first=async {engine.apply("test",plan)};yield()
        assertTrue(runCatching {engine.apply("test",plan)}.isFailure);first.await()
    }
    @Test fun aiCompanionAndProfileFieldsWork() {
        val p = PassengerProfile(
            profileId = "minjun",
            displayName = "민준",
            interestTopic = "기차와 역 이름",
            comfortPhrase = "이번 역을 지나 이모네 역으로 안전하게 가고 있어",
            weatherScene = "비 오는 길",
            preferences = Preferences(temperatureCelsius = 22.0f, fanPreference = "LOW", ambientBrightnessPercent = 20)
        )
        val encoded = profileJson.encodeToString(PassengerProfile.serializer(), p)
        val decoded = ProfileCodec.decode(encoded)
        assertEquals("기차와 역 이름", decoded.interestTopic)
        assertEquals("비 오는 길", decoded.weatherScene)
        val repeatTurn = AiCompanionEngine.repeatQuestionTurn(decoded, 3, true)
        assertTrue(repeatTurn.speakerLabel.contains("3번째"))
        assertTrue(repeatTurn.aiReply.contains("다시 알려줄게"))
        assertTrue(repeatTurn.aiReply.contains("이모네 역"))
        val (weatherTurn, weatherPrefs) = AiCompanionEngine.windowWeatherTurn(decoded, "비 오는 길", true)
        assertEquals(23.0f, weatherPrefs.temperatureCelsius)
        assertTrue(weatherTurn.speakerLabel.contains("비 오는 길"))
        val (_, sosPrefs) = AiCompanionEngine.sensorySosTurn(decoded, "LIGHT")
        assertEquals(10, sosPrefs.ambientBrightnessPercent)
    }
}
