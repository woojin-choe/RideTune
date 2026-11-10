package com.humanfirst.ridetune.vehicle

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.encodeToString
import java.io.File

class LocalStore(context: Context) {
    private val dir = context.filesDir
    private fun read(name: String): String? = AtomicFile(File(dir,name)).let { f -> if(f.baseFile.exists() || File(dir,"$name.bak").exists()) String(f.readFully()) else null }
    private fun write(name: String, value: String) {
        val file = AtomicFile(File(dir,name)); val out = file.startWrite()
        try { out.write(value.toByteArray()); file.finishWrite(out) } catch(t: Throwable) { file.failWrite(out); throw t }
    }
    fun profiles(): List<PassengerProfile> = read("profiles.json")?.let { profileJson.decodeFromString<List<PassengerProfile>>(it).also { ps -> ps.forEach(ProfileCodec::validate) } } ?: ProfileCodec.examples()
    fun saveProfiles(ps: List<PassengerProfile>) { ps.forEach(ProfileCodec::validate); write("profiles.json",profileJson.encodeToString(ps)) }
    fun session(): RideSession? = read("session.json")?.let { profileJson.decodeFromString<RideSession>(it) }?.takeUnless { it.closed }
    fun saveSession(s: RideSession) = write("session.json",profileJson.encodeToString(s))
}
