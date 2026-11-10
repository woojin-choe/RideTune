package com.humanfirst.ridetune.vehicle

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom

internal object SimulatorProfileReceiver {
    private const val port = 48732
    private const val maxBytes = 16384

    @Serializable
    private data class Request(val code: String, val profile: PassengerProfile? = null)

    fun newCode(): String = (100000 + SecureRandom().nextInt(900000)).toString()

    suspend fun receive(code: String): PassengerProfile? = withContext(Dispatchers.IO) {
        val expires = SystemClock.elapsedRealtime() + 120_000
        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
            server.soTimeout = 300
            var rejected = 0
            while (SystemClock.elapsedRealtime() < expires && rejected < 5) {
                ensureActive()
                val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                socket.use connection@{
                    it.soTimeout = 1500
                    val request = try {
                        val input = it.getInputStream().buffered()
                        val bytes = ByteArrayOutputStream()
                        val readDeadline = minOf(expires, SystemClock.elapsedRealtime() + 1500)
                        while (bytes.size() < maxBytes && SystemClock.elapsedRealtime() < readDeadline) {
                            ensureActive()
                            val byte = input.read()
                            if (byte == -1) break
                            if (byte == 10) {
                                bytes.write(byte)
                                break
                            }
                            bytes.write(byte)
                        }
                        val data = bytes.toByteArray()
                        if (data.lastOrNull()?.toInt() == 10 && SystemClock.elapsedRealtime() < readDeadline) {
                            profileJson.decodeFromString<Request>(data.decodeToString(throwOnInvalidSequence = true))
                        } else null
                    } catch (_: IOException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    ensureActive()
                    val profile = request?.profile
                    val status = when {
                        request == null -> 'E'
                        request.code != code -> { rejected++; 'N' }
                        profile == null -> 'O'
                        else -> if (runCatching { ProfileCodec.validate(profile) }.isSuccess) 'O' else 'E'
                    }
                    try {
                        it.getOutputStream().write(status.code)
                    } catch (_: IOException) {
                        return@connection
                    }
                    if (status == 'O' && profile != null) return@withContext profile
                }
            }
            null
        }
    }
}
