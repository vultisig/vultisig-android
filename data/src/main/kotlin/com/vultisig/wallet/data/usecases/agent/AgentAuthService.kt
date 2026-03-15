package com.vultisig.wallet.data.usecases.agent

import android.content.SharedPreferences
import androidx.core.content.edit
import com.vultisig.wallet.data.api.VerifierApi
import com.vultisig.wallet.data.api.VerifierAuthRequest
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.models.Vault
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import timber.log.Timber
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

@Singleton
class AgentAuthService
@Inject
constructor(
    private val verifierApi: VerifierApi,
    private val encryptedPrefs: SharedPreferences,
    private val json: Json,
) {

    private val cachedTokens = java.util.concurrent.ConcurrentHashMap<String, AuthToken>()

    fun getCachedToken(vaultPubKey: String): AuthToken? {
        var token = cachedTokens[vaultPubKey]

        if (token == null) {
            token = loadPersistedToken(vaultPubKey) ?: return null
            cachedTokens[vaultPubKey] = token
        }

        if (token.token.isBlank()) {
            invalidateToken(vaultPubKey)
            return null
        }

        val fiveMinutesFromNow = System.currentTimeMillis() + REFRESH_BUFFER_MS
        if (token.expiresAt < fiveMinutesFromNow) {
            return null
        }

        return token
    }

    suspend fun refreshIfNeeded(vaultPubKey: String): AuthToken? {
        val token = cachedTokens[vaultPubKey] ?: loadPersistedToken(vaultPubKey) ?: return null

        val fiveMinutesFromNow = System.currentTimeMillis() + REFRESH_BUFFER_MS
        if (token.expiresAt > fiveMinutesFromNow) {
            return token
        }

        if (token.refreshToken.isBlank()) {
            invalidateToken(vaultPubKey)
            return null
        }

        return try {
            val response = verifierApi.refreshToken(token.refreshToken)
            val newToken =
                buildAuthToken(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresIn = response.expiresIn,
                )
            cachedTokens[vaultPubKey] = newToken
            persistToken(vaultPubKey, newToken)
            newToken
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getOrRefreshToken(vaultPubKey: String): String? {
        val cached = getCachedToken(vaultPubKey)
        if (cached != null) return cached.token
        val refreshed = refreshIfNeeded(vaultPubKey)
        return refreshed?.token
    }

    suspend fun signIn(
        vault: Vault,
        signMessage: suspend (messageHash: String) -> String,
    ): AuthToken {
        val authMessage = generateAuthMessage(vault.pubKeyECDSA, vault.hexChainCode)
        // EIP-191 personal sign: prefix with "\x19Ethereum Signed Message:\n{len}"
        val messageBytes = authMessage.toByteArray(Charsets.UTF_8)
        val prefixed =
            "\u0019Ethereum Signed Message:\n${messageBytes.size}".toByteArray(Charsets.UTF_8) +
                messageBytes
        @OptIn(ExperimentalStdlibApi::class)
        val messageHash = prefixed.toKeccak256ByteArray().toHexString()

        val signature = signMessage(messageHash)

        val response =
            verifierApi.authenticate(
                VerifierAuthRequest(
                    message = authMessage,
                    signature = signature,
                    chainCodeHex = vault.hexChainCode,
                    publicKey = vault.pubKeyECDSA,
                )
            )

        if (response.accessToken.isBlank()) {
            error("Authentication returned empty token")
        }

        val token =
            buildAuthToken(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresIn = response.expiresIn,
            )
        cachedTokens[vault.pubKeyECDSA] = token
        persistToken(vault.pubKeyECDSA, token)
        return token
    }

    fun invalidateToken(vaultPubKey: String) {
        cachedTokens.remove(vaultPubKey)
        deletePersistedToken(vaultPubKey)
    }

    fun isSignedIn(vaultPubKey: String): Boolean = getCachedToken(vaultPubKey) != null

    @OptIn(ExperimentalStdlibApi::class)
    private fun generateAuthMessage(publicKeyEcdsa: String, hexChainCode: String): String {
        val pubKeyHex =
            PublicKeyHelper.getDerivedPublicKey(
                publicKeyEcdsa,
                hexChainCode,
                CoinType.ETHEREUM.derivationPath(),
            )
        val normalized = pubKeyHex.removePrefix("0x").removePrefix("0X")
        val pubKey = PublicKey(normalized.hexToByteArray(), PublicKeyType.SECP256K1)
        val address = AnyAddress(pubKey, CoinType.ETHEREUM).description()

        val expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES).toString()

        return json.encodeToString(
            AuthMessageJson.serializer(),
            AuthMessageJson(
                message = "Sign into Vultisig Plugin Marketplace",
                nonce = generateNonce(),
                expiresAt = expiresAt,
                address = address,
            ),
        )
    }

    private fun buildAuthToken(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
    ): AuthToken {
        var expiresAt =
            if (expiresIn > 0) {
                System.currentTimeMillis() + expiresIn * 1000
            } else {
                0L
            }
        if (expiresAt == 0L) {
            expiresAt = parseJwtExpiry(accessToken)
        }
        if (expiresAt == 0L) {
            error("Could not determine token expiry")
        }
        return AuthToken(token = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)
    }

    private fun loadPersistedToken(vaultPubKey: String): AuthToken? =
        try {
            val raw = encryptedPrefs.getString(getStorageKey(vaultPubKey), null) ?: return null
            val persisted = json.decodeFromString(PersistedAuthToken.serializer(), raw)
            if (persisted.token.isBlank()) null
            else
                AuthToken(
                    token = persisted.token,
                    refreshToken = persisted.refreshToken,
                    expiresAt = persisted.expiresAt,
                )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load persisted token for vault: $vaultPubKey")
            null
        }

    private fun persistToken(vaultPubKey: String, token: AuthToken) {
        try {
            val serialized =
                json.encodeToString(
                    PersistedAuthToken.serializer(),
                    PersistedAuthToken(token.token, token.refreshToken, token.expiresAt),
                )
            encryptedPrefs.edit { putString(getStorageKey(vaultPubKey), serialized) }
        } catch (_: Exception) {
            // storage unavailable
        }
    }

    private fun deletePersistedToken(vaultPubKey: String) {
        try {
            encryptedPrefs.edit { remove(getStorageKey(vaultPubKey)) }
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L
        private const val TOKEN_STORAGE_PREFIX = "vultisig_agent_auth_"

        private fun getStorageKey(vaultPubKey: String) = TOKEN_STORAGE_PREFIX + vaultPubKey
    }
}

data class AuthToken(val token: String, val refreshToken: String, val expiresAt: Long)

@Serializable
private data class PersistedAuthToken(
    val token: String,
    val refreshToken: String,
    val expiresAt: Long,
)

@Serializable
private data class AuthMessageJson(
    val message: String,
    val nonce: String,
    val expiresAt: String,
    val address: String,
)

private fun generateNonce(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return "0x" + bytes.joinToString("") { "%02x".format(it) }
}

private fun parseJwtExpiry(token: String): Long {
    return try {
        val parts = token.split(".")
        if (parts.size != 3) return 0L
        var payload = parts[1]
        when (payload.length % 4) {
            2 -> payload += "=="
            3 -> payload += "="
        }
        val decoded =
            String(
                android.util.Base64.decode(
                    payload.replace('-', '+').replace('_', '/'),
                    android.util.Base64.DEFAULT,
                )
            )
        val jsonElement = Json.parseToJsonElement(decoded)
        val exp =
            (jsonElement as? JsonObject)?.get("exp")?.let { it as? JsonPrimitive }?.long
                ?: return 0L
        exp * 1000L
    } catch (_: Exception) {
        0L
    }
}
