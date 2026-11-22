package com.vultisig.wallet.data.mediator

import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Authentication of the Android LAN transport using the one-time ceremony QR secret. */
class LocalMediatorAuth private constructor(val sessionId: String, private val key: ByteArray) {
    fun newNonce(): String = ByteArray(16).also(random::nextBytes).let(encoder::encodeToString)

    fun requestTag(
        method: String,
        target: String,
        messageId: String?,
        timestamp: Long,
        nonce: String,
        payloadDigest: String,
    ): String =
        tag("request", method, target, messageId.orEmpty(), timestamp.toString(), nonce, payloadDigest)

    fun responseTag(requestTag: String, status: Int, body: ByteArray): String =
        tag("response", requestTag, status.toString(), digest(body))

    fun matches(expected: String, actual: String?): Boolean =
        actual != null &&
            actual.length == TAG_LENGTH &&
            MessageDigest.isEqual(expected.encodeToByteArray(), actual.encodeToByteArray())

    private fun tag(vararg fields: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        // Length-prefix each field so the header and request-target boundaries are unambiguous.
        for (field in fields) {
            val bytes = field.encodeToByteArray()
            mac.update(bytes.size.toString().encodeToByteArray())
            mac.update(':'.code.toByte())
            mac.update(bytes)
        }
        return encoder.encodeToString(mac.doFinal())
    }

    companion object {
        const val PORT = 18080
        const val DNS_SERVICE_TYPE = "_vultisig-lan._tcp."
        const val SESSION_DURATION_MS = 30 * 60 * 1000L
        const val MAX_REQUEST_BYTES = 1024 * 1024
        const val MAX_RESPONSE_BYTES = 10 * 1024 * 1024
        const val TIMESTAMP_HEADER = "X-Vultisig-Lan-Time"
        const val NONCE_HEADER = "X-Vultisig-Lan-Nonce"
        const val DIGEST_HEADER = "X-Vultisig-Lan-Body"
        const val AUTH_HEADER = "X-Vultisig-Lan-Auth"
        const val RESPONSE_HEADER = "X-Vultisig-Lan-Response"

        private const val ALGORITHM = "HmacSHA256"
        private const val TAG_LENGTH = 43
        private val sessionPattern = Regex("[a-zA-Z0-9-]{1,64}")
        private val keyPattern = Regex("[a-fA-F0-9]{64}")
        private val random = SecureRandom()
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        fun create(sessionId: String, encryptionKeyHex: String): LocalMediatorAuth? {
            if (!sessionPattern.matches(sessionId) || !keyPattern.matches(encryptionKeyHex)) {
                return null
            }
            val secret = ByteArray(32) { index ->
                encryptionKeyHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            return try {
                val derivation = Mac.getInstance(ALGORITHM)
                derivation.init(SecretKeySpec(secret, ALGORITHM))
                val key = derivation.doFinal("vultisig-android-lan-v1:$sessionId".encodeToByteArray())
                LocalMediatorAuth(sessionId, key)
            } finally {
                secret.fill(0)
            }
        }

        fun digest(body: ByteArray): String =
            encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(body))
    }
}

/** Keeps only the active LAN ceremony's bindings available to the HTTP client. */
object LocalMediatorCredentials {
    class Binding internal constructor(val auth: LocalMediatorAuth, val expiresAt: Long) {
        @Volatile var hostVerified = false
    }

    private const val MAX_BINDINGS = 4
    private val credentialByOrigin = LinkedHashMap<String, Binding>()

    @Synchronized
    fun bind(origin: String, auth: LocalMediatorAuth): Boolean {
        val key = localOrigin(origin) ?: return false
        val now = System.currentTimeMillis()
        credentialByOrigin.entries.removeAll { it.value.expiresAt <= now }
        if (key !in credentialByOrigin && credentialByOrigin.size >= MAX_BINDINGS) return false
        credentialByOrigin[key] = Binding(auth, now + LocalMediatorAuth.SESSION_DURATION_MS)
        return true
    }

    @Synchronized
    fun get(origin: String): Binding? {
        val binding = credentialByOrigin[origin] ?: return null
        if (binding.expiresAt <= System.currentTimeMillis()) {
            credentialByOrigin.remove(origin)
            return null
        }
        return binding
    }

    @Synchronized
    fun clear(origin: String, sessionId: String) {
        if (credentialByOrigin[origin]?.auth?.sessionId == sessionId) credentialByOrigin.remove(origin)
    }

    private fun localOrigin(input: String): String? {
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val fields = host.split('.')
        if (
            uri.scheme != "http" ||
                uri.port != LocalMediatorAuth.PORT ||
                (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") ||
                uri.rawUserInfo != null ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                fields.size != 4
        ) return null
        val bytes = ByteArray(4)
        for ((index, field) in fields.withIndex()) {
            val n = field.toIntOrNull() ?: return null
            if (n !in 0..255 || field != n.toString()) return null
            bytes[index] = n.toByte()
        }
        val ip = InetAddress.getByAddress(bytes)
        if (!ip.isSiteLocalAddress && !ip.isLinkLocalAddress && !ip.isLoopbackAddress) return null
        return "http://$host:" + uri.port
    }
}

internal class MediatorReplayWindow {
    private val seen = LinkedHashMap<String, Long>()
    private val noncePattern = Regex("[A-Za-z0-9_-]{22}")

    @Synchronized
    fun accept(timestamp: Long, nonce: String, now: Long): Boolean {
        if (!noncePattern.matches(nonce) || timestamp !in (now - WINDOW_MS)..(now + WINDOW_MS)) {
            return false
        }
        seen.entries.removeAll { it.value < now }
        if (nonce in seen || seen.size >= MAX_NONCES) return false
        seen[nonce] = timestamp + WINDOW_MS
        return true
    }

    @Synchronized
    fun clear() = seen.clear()

    private companion object {
        const val WINDOW_MS = 2 * 60 * 1000L
        const val MAX_NONCES = 16_384
    }
}
