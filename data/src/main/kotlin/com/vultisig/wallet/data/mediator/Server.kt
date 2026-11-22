package com.vultisig.wallet.data.mediator

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.vultisig.wallet.data.common.sha256
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import spark.Request
import spark.Response
import spark.Route
import spark.Service
import timber.log.Timber

internal class Server(
    private val nsdManager: NsdManager,
    private val authentication: LocalMediatorAuth,
) : NsdManager.RegistrationListener {
    private val service = Service.ignite()
    private val replayWindow = MediatorReplayWindow()
    private val cache = MediatorCache()
    private val stateLock = Any()
    private val expiresAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LocalMediatorAuth.SESSION_DURATION_MS)
    private var active = true
    private var participants = emptyList<String>()
    private var committee: List<String>? = null
    private var completed = emptyList<String>()
    private val json = Json { ignoreUnknownKeys = true }

    fun startMediator(name: String) {
        service.port(LocalMediatorAuth.PORT)
        service.threadPool(32, 4, 15_000)
        service.internalServerError("Internal Server Error")
        setupRouting()
        val serviceInfo =
            NsdServiceInfo().apply {
                serviceName = name
                serviceType = LocalMediatorAuth.DNS_SERVICE_TYPE.removeSuffix(".")
                port = LocalMediatorAuth.PORT
            }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, this)
    }

    private fun setupRouting() {
        service.get("/auth/:sessionID", authenticated { _, _ -> ok() })
        service.get("/:sessionID", authenticated { _, _ -> listResponse(participants) })
        service.post("/:sessionID", authenticated { _, body ->
            val updated = (participants + readParties(body)).distinct()
            require(updated.size <= MAX_PARTICIPANTS)
            participants = updated
            Result(201)
        })
        service.delete("/:sessionID", authenticated(hostOnly = true) { _, _ ->
            clearState()
            active = false
            ok()
        })

        service.get("/start/:sessionID", authenticated { _, _ -> listResponse(committee) })
        service.post("/start/:sessionID", authenticated(hostOnly = true) { _, body ->
            val selected = readParties(body)
            require(selected.isNotEmpty() && selected.all { it in participants })
            check(committee == null || committee == selected)
            committee = selected
            ok()
        })
        service.get("/complete/:sessionID", authenticated { _, _ -> listResponse(completed) })
        service.post("/complete/:sessionID", authenticated { _, body ->
            val parties = readParties(body)
            require(parties.all { it in participants })
            completed = (completed + parties).distinct()
            Result(201)
        })

        service.get("/payload/:hash", authenticated { request, _ ->
            getPayload(cacheKeyForPayload(request))
        })
        service.post("/payload/:hash", authenticated(hostOnly = true) { request, body ->
            val key = cacheKeyForPayload(request)
            require(body.sha256() == key.id)
            store(listOf(key), body, 201)
        })
        service.get("/setup-message/:sessionID", authenticated { request, _ ->
            getPayload(MediatorCacheKey("setup", messageId = messageId(request)))
        })
        service.post("/setup-message/:sessionID", authenticated { request, body ->
            store(listOf(MediatorCacheKey("setup", messageId = messageId(request))), body, 201)
        })
        service.get("/complete/:sessionID/keysign", authenticated { request, _ ->
            getPayload(MediatorCacheKey("signature", messageId = requiredMessageId(request)))
        })
        service.post("/complete/:sessionID/keysign", authenticated { request, body ->
            val key = MediatorCacheKey("signature", messageId = requiredMessageId(request))
            store(listOf(key), body, 200)
        })

        service.post("/message/:sessionID", authenticated { request, body ->
            val message = json.decodeFromString<Message>(body)
            require(message.sessionID == authentication.sessionId)
            require(committee?.contains(message.from) == true)
            require(message.to.size in 1..MAX_PARTICIPANTS)
            require(message.to.all { committee?.contains(it) == true })
            require(message.hash.length in 1..MAX_ID_CHARS)
            val id = messageId(request)
            val keys = message.to.map { MediatorCacheKey("message", message.hash, it, id) }
            store(keys, body, 202)
        })
        service.get("/message/:sessionID/:participantKey", authenticated { request, _ ->
            val party = partyParam(request)
            val id = messageId(request)
            val messages = cache.matching {
                it.kind == "message" && it.participant == party && it.messageId == id
            }
            ok(messages.joinToString(prefix = "[", postfix = "]"))
        })
        service.delete("/message/:sessionID/:participantKey/:hash", authenticated { request, _ ->
            val hash = request.params(":hash")
            require(hash.length in 1..MAX_ID_CHARS)
            cache.remove(MediatorCacheKey("message", hash, partyParam(request), messageId(request)))
            ok()
        })
    }

    private fun authenticated(
        hostOnly: Boolean = false,
        handler: (Request, String) -> Result,
    ) = Route { request, response ->
        val stamp = authorize(request)
        if (stamp == null) {
            response.status(401)
            "Local mediator authentication required"
        } else {
            val result =
                try {
                    if (hostOnly && !InetAddress.getByName(request.ip()).isLoopbackAddress) {
                        Result(403)
                    } else {
                        val body = readBody(request, stamp.bodyDigest)
                        synchronized(stateLock) {
                            if (active && System.nanoTime() >= expiresAt) {
                                clearState()
                                active = false
                            }
                            if (active) handler(request, body) else Result(410)
                        }
                    }
                } catch (_: LimitExceeded) {
                    Result(413)
                } catch (_: IOException) {
                    Result(400)
                } catch (_: IllegalArgumentException) {
                    Result(400)
                } catch (_: IllegalStateException) {
                    Result(409)
                }
            send(response, stamp.tag, result)
        }
    }

    private fun authorize(request: Request): AuthenticatedRequest? {
        val raw = request.raw()
        val target = raw.requestURI
        if (raw.queryString != null || target.length > MAX_TARGET_CHARS) return null
        val sessionId = request.params(":sessionID")
        if (sessionId != null && sessionId != authentication.sessionId) return null
        val messageId = request.headers("message_id")
        if (messageId != null && messageId.length > MAX_ID_CHARS) return null
        val timestamp = request.headers(LocalMediatorAuth.TIMESTAMP_HEADER)?.toLongOrNull()
            ?: return null
        val nonce = request.headers(LocalMediatorAuth.NONCE_HEADER) ?: return null
        val bodyDigest = request.headers(LocalMediatorAuth.DIGEST_HEADER) ?: return null
        if (nonce.length != 22 || bodyDigest.length != 43) return null
        val tag = authentication.requestTag(raw.method, target, messageId, timestamp, nonce, bodyDigest)
        if (!authentication.matches(tag, request.headers(LocalMediatorAuth.AUTH_HEADER))) return null
        if (!replayWindow.accept(timestamp, nonce, System.currentTimeMillis())) return null
        return AuthenticatedRequest(tag, bodyDigest)
    }

    private fun readBody(request: Request, expectedDigest: String): String {
        val raw = request.raw()
        if (raw.contentLengthLong > LocalMediatorAuth.MAX_REQUEST_BYTES) throw LimitExceeded()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        raw.inputStream.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > LocalMediatorAuth.MAX_REQUEST_BYTES) throw LimitExceeded()
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        require(authentication.matches(LocalMediatorAuth.digest(bytes), expectedDigest))
        if (request.requestMethod() != "POST") require(bytes.isEmpty())
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }

    private fun send(response: Response, requestTag: String, result: Result): String {
        response.status(result.status)
        response.type("application/json; charset=utf-8")
        response.header("Cache-Control", "no-store")
        response.header(
            LocalMediatorAuth.RESPONSE_HEADER,
            authentication.responseTag(requestTag, result.status, result.body.encodeToByteArray()),
        )
        return result.body
    }

    private fun getPayload(key: MediatorCacheKey): Result =
        cache[key]?.let { ok(it) } ?: Result(404)

    private fun store(keys: List<MediatorCacheKey>, value: String, status: Int): Result =
        if (cache.add(keys, value)) Result(status) else Result(507)

    private fun readParties(body: String): List<String> {
        val parts = json.decodeFromString<List<String>>(body)
        require(parts.size <= MAX_PARTICIPANTS && parts.all(::validParty))
        return parts.distinct()
    }

    private fun partyParam(request: Request): String {
        val id = request.params(":participantKey")
        require(validParty(id) && committee?.contains(id) == true)
        return id
    }

    private fun validParty(id: String): Boolean =
        id.isNotBlank() && id.length <= MAX_ID_CHARS && id.none(Char::isISOControl)

    private fun messageId(request: Request): String? = request.headers("message_id")
    private fun requiredMessageId(request: Request): String = requireNotNull(messageId(request))

    private fun cacheKeyForPayload(request: Request): MediatorCacheKey {
        val hash = request.params(":hash")
        require(payloadHashPattern.matches(hash))
        return MediatorCacheKey("payload", hash)
    }

    private fun listResponse(list: List<String>?): Result =
        if (list.isNullOrEmpty()) Result(404) else ok(json.encodeToString(list))

    private fun ok(body: String = "") = Result(200, body)

    private fun clearState() {
        cache.clear()
        participants = emptyList()
        committee = null
        completed = emptyList()
    }

    fun stopServer() {
        synchronized(stateLock) {
            active = false
            clearState()
            replayWindow.clear()
        }
        service.stop()
        runCatching { nsdManager.unregisterService(this) }
    }

    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Timber.tag("Server").e("Local mediator registration failed: %d", errorCode)
    }

    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Timber.tag("Server").e("Local mediator unregistration failed: %d", errorCode)
    }

    override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) = Unit

    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) = Unit

    private data class AuthenticatedRequest(val tag: String, val bodyDigest: String)
    private data class Result(val status: Int, val body: String = "")
    private class LimitExceeded : IOException()

    private companion object {
        const val MAX_ID_CHARS = 256
        const val MAX_TARGET_CHARS = 4_096
        const val MAX_PARTICIPANTS = 64
        val payloadHashPattern = Regex("[a-f0-9]{64}")
    }
}
