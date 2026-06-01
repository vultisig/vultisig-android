package com.vultisig.wallet.data.blockchain.cosmos.staking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Looks up the validator avatar URL from the Keybase public lookup API given the validator's
 * `description.identity` (16-hex string by Cosmos convention). Caches the URL — including the "no
 * avatar" negative cache — per identity with a 1-hour TTL. Mirrors the Windows
 * `useKeybaseAvatarQuery` hook (1-hour staleTime + no retry) and iOS `KeybaseAvatarService.swift`.
 *
 * Endpoint:
 *
 *     https://keybase.io/_/api/1.0/user/lookup.json?key_suffix={identity}&fields=pictures
 *
 * Parse path: `them[0].pictures.primary.url`. When the identity has no associated Keybase profile
 * picture, the resolver returns `null` and the validator card falls back to the deterministic
 * monogram avatar.
 */
interface KeybaseAvatarService {
    suspend fun avatarUrl(identity: String): String?
}

@Singleton
internal class KeybaseAvatarServiceImpl @Inject constructor(private val httpClient: HttpClient) :
    KeybaseAvatarService {

    /**
     * Clock + TTL are `internal var` so tests can pin them; not in the `@Inject` constructor
     * because Dagger ignores Kotlin default-valued params.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var ttlMillis: Long = 60L * 60L * 1000L

    private data class CachedEntry(val url: String?, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedEntry>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<String?>>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun avatarUrl(identity: String): String? {
        val trimmed = identity.trim()
        if (trimmed.isEmpty()) return null

        val deferred: CompletableDeferred<String?> =
            mutex.withLock {
                cache[trimmed]?.let { entry ->
                    if (clock() - entry.fetchedAt < ttlMillis) {
                        return entry.url
                    }
                }
                inFlight[trimmed]?.let {
                    return@withLock it
                }
                val newDeferred = CompletableDeferred<String?>()
                inFlight[trimmed] = newDeferred
                newDeferred
            }

        if (!deferred.isCompleted && deferred === inFlight[trimmed]) {
            val result =
                try {
                    fetch(trimmed)
                } catch (e: Throwable) {
                    Timber.w(e, "Keybase lookup failed for identity %s", trimmed)
                    null
                }
            mutex.withLock {
                inFlight.remove(trimmed)
                cache[trimmed] = CachedEntry(result, clock())
            }
            deferred.complete(result)
        }
        return deferred.await()
    }

    private suspend fun fetch(identity: String): String? {
        val raw =
            httpClient
                .get(KEYBASE_LOOKUP_URL) {
                    parameter("key_suffix", identity)
                    parameter("fields", "pictures")
                }
                .bodyAsText()
        val parsed = runCatching { json.decodeFromString<KeybaseLookupResponse>(raw) }.getOrNull()
        val candidate =
            parsed
                ?.them
                ?.asSequence()
                ?.mapNotNull { it?.pictures?.primary?.url }
                ?.firstOrNull { it.isNotEmpty() }
        return candidate
    }

    companion object {
        private const val KEYBASE_LOOKUP_URL = "https://keybase.io/_/api/1.0/user/lookup.json"
    }
}

/**
 * Keybase's `user/lookup.json` returns `them` as a *nullable list of nullable entries* — when the
 * identity isn't registered, the entire `them` field is `null`; some lookups return a single null
 * slot. Match that shape exactly so a missing avatar collapses to `null` without throwing.
 */
@Serializable
internal data class KeybaseLookupResponse(val them: List<Entry?>? = null) {
    @Serializable
    data class Entry(val pictures: Pictures? = null) {
        @Serializable data class Pictures(val primary: Picture? = null)

        @Serializable data class Picture(@SerialName("url") val url: String? = null)
    }
}
