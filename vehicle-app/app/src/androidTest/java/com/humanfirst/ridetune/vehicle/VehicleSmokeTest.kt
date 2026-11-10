package com.humanfirst.ridetune.vehicle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.util.Log

@RunWith(AndroidJUnit4::class)
class VehicleSmokeTest {
    @Test fun profileSessionSurvivesReloadAndRestores() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val g = withContext(Dispatchers.Main) { PleosVehicleGateway(context) }
        val store = LocalStore(context)
        var journal: RideSession? = null
        val engine = SessionEngine(g) { journal = it; store.saveSession(it) }
        try {
            delay(1500)
            val caps = g.capabilities()
            val current = caps.filter { it.readable }.associate { "${it.feature}:${it.area}" to g.read(it.feature,it.area) }
            val plan = buildPlan(ProfileCodec.examples().first(),caps,current)
            assertTrue(plan.any { it.actionable })
            val result = engine.apply("검증 프로필",plan)
            for(e in result.entries.filter { it.item.actionable }) assertEquals(e.toString(),"적용 확인",e.status)
            val reloaded = LocalStore(context).session()!!
            assertEquals(result,reloaded)
            val restored = SessionEngine(g) { journal=it;store.saveSession(it) }.restore(reloaded,false)
            assertTrue(restored.toString(),restored.closed)
            assertNull(LocalStore(context).session())
            Log.i("RideTuneTest","Combined profile apply / journal reload / restore verified")
        } finally {
            journal?.takeIf { !it.closed }?.let { engine.restore(it,false) }
            withContext(Dispatchers.Main) { g.close() }
        }
    }
    @Test fun realSdkRoundTrip() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val g = withContext(Dispatchers.Main) { PleosVehicleGateway(context) }
        try {
            delay(1500)
            assertTrue("P단 확인",g.parked())
            val caps = g.capabilities()
            val report = StringBuilder("SDK 2.3.2 / Pleos AVD\n")
            caps.forEach { report.appendLine(it) }
            for(f in listOf(Feature.TEMPERATURE,Feature.FAN,Feature.BRIGHTNESS,Feature.COLOR,Feature.LIGHT)) {
                val c = caps.first { it.feature == f && it.writable }
                val old = g.read(f,c.area)
                val new = when(f) {
                    Feature.TEMPERATURE -> if(old.toFloat() == 22f) "23.0" else "22.0"
                    Feature.FAN -> if(old == "2") "3" else "2"
                    Feature.BRIGHTNESS -> if(old == "20") "30" else "20"
                    Feature.COLOR -> if(old == "#5588CC") "#70B89B" else "#5588CC"
                    else -> if(old == "ON") "OFF" else "ON"
                }
                try {
                    g.write(f,c.area,new); delay(400)
                    val actual = g.read(f,c.area)
                    report.appendLine("$f: before=$old request=$new observed=$actual")
                    assertTrue("$f readback $actual != $new",sameValue(f,new,actual))
                } finally { g.write(f,c.area,old); delay(400); assertTrue("$f restore",sameValue(f,old,g.read(f,c.area))) }
                report.appendLine("$f restore verified")
            }
            context.openFileOutput("sdk-verification.txt",0).use { it.write(report.toString().toByteArray()) }
            Log.i("RideTuneTest",report.toString())
        } finally { withContext(Dispatchers.Main) {g.close()} }
    }
}
