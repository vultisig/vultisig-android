package com.vultisig.wallet.data.mediator

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.vultisig.wallet.data.common.sha256
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import spark.Request
import spark.Response
import spark.Service
import timber.log.Timber

class Server(private val nsdManager: NsdManager) : NsdManager.RegistrationListener {
    private val port = 18080
    private val cache = mutableMapOf<String, Any>()

    private val service: Service = Service.ignite()

    fun startMediator(name: String) {
        try {
            this.service.port(port)
            this.service.internalServerError("Internal Server Error")
            setupRouting(this.service)
            registerService(name)
            Timber.tag("Server").d("Server started on port %s", port)
        } catch (e: Exception) {
            Timber.tag("Server").e("Server start failed: %s", e.message)
        }
    }

    private fun registerService(name: String) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = name
                serviceType = "_http._tcp"
            }
            serviceInfo.port = port
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                this
            )
        } catch (e: IllegalArgumentException) {
            Timber.tag("Server").e("Service registration failed: %s", e.message)
            this.nsdManager.unregisterService(this)
        } catch(e: Exception) {
            Timber.tag("Server").e("Service registration failed: %s", e.message)
        }
    }

    private fun setupRouting(service: Service) {
        service.get("/:sessionID") { request, response -> getSession(request, response) }
        service.post("/:sessionID") { request, response -> postSession(request, response) }
        service.delete("/:sessionID") { request, response -> deleteSession(request, response) }
        service.post("/message/:sessionID") { request, response -> postMessage(request, response) }
        service.get("/message/:sessionID/:participantKey") { request, response ->
            getMessage(
                request,
                response
            )
        }
        service.delete("/message/:sessionID/:participantKey/:hash") { request, response ->
            deleteMessage(
                request,
                response
            )
        }
        service.post("/complete/:sessionID/keysign") { request, response ->
            postCompleteKeySign(
                request,
                response
            )
        }
        service.get("/complete/:sessionID/keysign") { request, response ->
            getCompleteKeySign(
                request,
                response
            )
        }
        service.get("/start/:sessionID") { request, response ->
            getStartKeygenOrKeysign(
                request,
                response
            )
        }
        service.post("/start/:sessionID") { request, response ->
            postStartKeygenOrKeysign(
                request,
                response
            )
        }
        service.get("/complete/:sessionID") { request, response ->
            getSession(
                request,
                response,
                "complete"
            )
        }
        service.post("/complete/:sessionID") { request, response ->
            postSession(
                request,
                response,
                "complete"
            )
        }
        service.post("/payload/:hash") { request, response ->
            postPayload(
                request,
                response
            )
        }
        service.get("/payload/:hash") { request, response ->
            getPayload(
                request,
                response
            )
        }
        service.post("/setup-message/:sessionID") { request, response ->
            postSetupMessage(
                request,
                response
            )
        }
        service.get("/setup-message/:sessionID") { request, response ->
            getSetupMessage(
                request,
                response
            )
        }
    }

    private fun getSetupMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        if (sessionID.isNullOrEmpty()) {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val messageID = request.headers("message_id")
        var key = "setup-$sessionID"
        if (!messageID.isNullOrEmpty()) {
            key = "$key-$messageID"
        }
        cache[key]?.let {
            val content = it as? String
            response.status(HttpStatusCode.OK.value)
            return content.toString()
        } ?: run {
            Timber.tag("Server").d("setup message not found for key: %s", key)
            response.status(HttpStatusCode.NotFound.value)
        }
        return ""
    }

    private fun postSetupMessage(request: Request, response: Response) {
        val sessionID = request.params(":sessionID")
        if (sessionID.isNullOrEmpty()) {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return
        }
        val messageID = request.headers("message_id")
        Timber.d("upload setup message: %s", sessionID)
        var key = "setup-$sessionID"
        if (!messageID.isNullOrEmpty()) {
            key = "$key-$messageID"
        }
        cache.put(key, request.body())
        response.status(HttpStatusCode.Created.value)
    }
    private fun getPayload(request: Request, response: Response): String {
        val hash = request.params(":hash")
        if (hash.isNullOrEmpty()) {
            response.status(HttpStatusCode.BadRequest.value)
            return "hash is empty"
        }
        cache[hash]?.let {
            val content = it as? String
            val contentHash = content?.sha256()
            if (contentHash != hash) {
                response.status(HttpStatusCode.BadRequest.value)
                return "hash mismatch"
            }

            Timber.d("return hash: %s", hash)
            response.status(HttpStatusCode.OK.value)
            return content
        } ?: run {
            response.status(HttpStatusCode.NotFound.value)
        }
        return ""
    }

    private fun postPayload(request: Request, response: Response) {
        val hash = request.params(":hash")
        if (hash.isNullOrEmpty()) {
            response.body("hash is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return
        }
        Timber.d("upload hash: %s", hash)
        val body = request.body()
        val bodyHash = body.sha256()
        if (bodyHash != hash) {
            response.body("hash mismatch")
            response.status(HttpStatusCode.BadRequest.value)
            return
        }
        cache.put(hash, request.body())
        response.status(HttpStatusCode.Created.value)
    }

    private fun postStartKeygenOrKeysign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val key = "session-$sessionID-start"
        val participants: List<String> = Json.decodeFromString(request.body())
        cache.put(key, Session(sessionID, participants.toMutableList()))
        response.status(HttpStatusCode.OK.value)
        response.type("application/json")
        return ""
    }

    private fun getStartKeygenOrKeysign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val key = "session-$sessionID-start"
        cache[key]?.let {
            val session = it as? Session
            session?.let {
                response.type("application/json")
                response.status(HttpStatusCode.OK.value)
                return Json.encodeToString(session.participants)
            } ?: response.status(HttpStatusCode.NotFound.value)
        } ?: response.status(HttpStatusCode.NotFound.value)
        return ""
    }

    private fun getCompleteKeySign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val messageID = request.headers("message_id")
        messageID ?: run {
            response.body("message_id is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val key = "keysign-${sessionID.trim()}-$messageID-complete"
        cache[key]?.let {
            response.type("application/json")
            response.status(HttpStatusCode.OK.value)
            return it as String
        } ?: run {
            response.status(HttpStatusCode.NotFound.value)
        }
        return ""
    }

    private fun postCompleteKeySign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val messageID = request.headers("message_id")
        messageID ?: run {
            response.body("message_id is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val key = "keysign-${sessionID.trim()}-$messageID-complete"
        cache.put(key, request.body())
        response.status(HttpStatusCode.OK.value)
        response.type("application/json")
        return ""
    }

    private fun deleteMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val participantKey = request.params(":participantKey")
        participantKey ?: run {
            response.body("participantKey is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val hash = request.params(":hash")
        hash ?: run {
            response.body("hash is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val messageID = request.headers("message_id")
        val key = messageID?.let {
            "$sessionID-$participantKey-$it-$hash"
        } ?: run {
            "$sessionID-$participantKey-$hash"
        }
        cache.remove(key)
        response.status(HttpStatusCode.OK.value)
        return ""
    }

    private fun getMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val participantKey = request.params(":participantKey")
        participantKey ?: run {
            response.body("participantKey is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val messageID = request.headers("message_id")
        val keyPrefix = messageID?.let {
            "${sessionID.trim()}-${participantKey.trim()}-$it-"
        } ?: run {
            "${sessionID.trim()}-${participantKey.trim()}-"
        }
        cache.filterKeys { it.startsWith(keyPrefix) }.let {
            val messages = it.values.toList().map { it as Message }
            response.status(HttpStatusCode.OK.value)
            response.type("application/json")
            return Json.encodeToString(messages)
        }

    }

    private fun postMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val messageID = request.headers("message_id")
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val message = json.decodeFromString<Message>(request.body())
        for (recipient in message.to) {
            val key = messageID?.let {
                "${sessionID.trim()}-$recipient-$it-${message.hash}"
            } ?: run {
                "$sessionID-$recipient-${message.hash}"
            }
            Timber.tag("server").d("put message %s", key)
            cache.put(key, message)
        }
        response.status(HttpStatusCode.Accepted.value)
        return ""
    }

    private fun postSession(
        request: Request,
        response: Response,
        prefix: String? = null,
    ): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val cleanSessionID = sessionID.trim()
        val key = if (!prefix.isNullOrBlank()) {
            "$prefix-session-$cleanSessionID"
        } else {
            "session-$cleanSessionID"
        }
        Timber.tag("server").d("body: %s", request.body())
        val participants: List<String> = Json.decodeFromString(request.body())
        cache[key]?.let {
            val session = it as? Session
            session?.let {
                for (participant in participants) {
                    if (!session.participants.contains(participant)) {
                        session.participants.add(participant)
                    }
                }
                cache.put(key, session)
            } ?: run {
                val newParticipants = Session(sessionID, participants.toMutableList())
                cache.put(key, newParticipants)
            }
        } ?: run {
            val newParticipants = Session(sessionID, participants.toMutableList())
            cache.put(key, newParticipants)
        }

        response.status(HttpStatusCode.Created.value)
        return ""
    }

    private fun getSession(request: Request, response: Response, prefix: String? = null): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val cleanSessionID = sessionID.trim()
        val key = if (!prefix.isNullOrBlank()) {
            "$prefix-session-$cleanSessionID"
        } else {
            "session-$cleanSessionID"
        }
        Timber.tag("server").d("get session %s", key)
        cache[key]?.let {
            val session = it as? Session
            session?.let {
                response.status(HttpStatusCode.OK.value)
                response.type("application/json")
                return Json.encodeToString(session.participants)
            } ?: response.status(HttpStatusCode.NotFound.value)
        } ?: response.status(HttpStatusCode.NotFound.value)
        return ""
    }

    private fun deleteSession(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatusCode.BadRequest.value)
            return ""
        }
        val cleanSessionID = sessionID.trim()
        val key = "session-$cleanSessionID"
        val keyStart = "$key-start"
        cache.remove(key)
        cache.remove(keyStart)
        return ""
    }

    fun stopServer() {
        try {
            Timber.tag("Server").d("Stopping server on port %s", port)
            this.service.stop()
            // clear cache
            cache.clear()
            nsdManager.unregisterService(this)
        }catch (e: Exception) {
            Timber.tag("Server").e("Server stop failed: %s", e.message)
        }
    }

    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Timber.tag("Server").e("Service registration failed: %s", errorCode)
    }

    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Timber.tag("Server").e("Service unregistration failed: %s", errorCode)
    }

    override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
        val serviceName = serviceInfo?.serviceName ?: ""
        Timber.tag("Server").d("Service registered: %s", serviceName)
    }

    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
        Timber.tag("Server").d("Service unregistered: %s", serviceInfo?.serviceName)
    }
}
