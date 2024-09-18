package com.vultisig.wallet.data.mediator

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import spark.Request
import spark.Response
import spark.Service
import timber.log.Timber

class Server(private val nsdManager: NsdManager) : NsdManager.RegistrationListener {
    private val port = 18080
    private val cache: Cache<String, Any> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build()
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
    }

    private fun postStartKeygenOrKeysign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val key = "session-$sessionID-start"
        val decodeType = object : TypeToken<List<String>>() {}.type
        val participants: List<String> = Gson().fromJson(request.body(), decodeType)
        cache.put(key, Session(sessionID, participants.toMutableList()))
        response.status(HttpStatus.OK)
        response.type("application/json")
        return ""
    }

    private fun getStartKeygenOrKeysign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val key = "session-$sessionID-start"
        cache.getIfPresent(key)?.let {
            val session = it as? Session
            session?.let {
                response.type("application/json")
                response.status(HttpStatus.OK)
                return Gson().toJson(session.participants)
            } ?: response.status(HttpStatus.NOT_FOUND)
        } ?: response.status(HttpStatus.NOT_FOUND)
        return ""
    }

    private fun getCompleteKeySign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val messageID = request.headers("message_id")
        messageID ?: run {
            response.body("message_id is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val key = "keysign-${sessionID.trim()}-$messageID-complete"
        cache.getIfPresent(key)?.let {
            response.type("application/json")
            response.status(HttpStatus.OK)
            return it as String
        } ?: run {
            response.status(HttpStatus.NOT_FOUND)
        }
        return ""
    }

    private fun postCompleteKeySign(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val messageID = request.headers("message_id")
        messageID ?: run {
            response.body("message_id is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val key = "keysign-${sessionID.trim()}-$messageID-complete"
        cache.put(key, request.body())
        response.status(HttpStatus.OK)
        response.type("application/json")
        return ""
    }

    private fun deleteMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val participantKey = request.params(":participantKey")
        participantKey ?: run {
            response.body("participantKey is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val hash = request.params(":hash")
        hash ?: run {
            response.body("hash is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val messageID = request.headers("message_id")
        val key = messageID?.let {
            "$sessionID-$participantKey-$it-$hash"
        } ?: run {
            "$sessionID-$participantKey-$hash"
        }
        cache.invalidate(key)
        response.status(HttpStatus.OK)
        return ""
    }

    private fun getMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val participantKey = request.params(":participantKey")
        participantKey ?: run {
            response.body("participantKey is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val messageID = request.headers("message_id")
        val keyPrefix = messageID?.let {
            "${sessionID.trim()}-${participantKey.trim()}-$it-"
        } ?: run {
            "${sessionID.trim()}-${participantKey.trim()}-"
        }
        cache.asMap().filterKeys { it.startsWith(keyPrefix) }.let {
            val messages = it.values.toList()
            response.status(HttpStatus.OK)
            response.type("application/json")
            return Gson().toJson(messages)
        }
    }

    private fun postMessage(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
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
        response.status(HttpStatus.ACCEPTED)
        return ""
    }

    private fun postSession(
        request: Request,
        response: Response,
        prefix: String? = null,
    ): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val cleanSessionID = sessionID.trim()
        val key = if (prefix != null) {
            "$prefix-session-$cleanSessionID"
        } else {
            "session-$cleanSessionID"
        }
        Timber.tag("server").d("body: %s", request.body())
        val gson = Gson()
        val decodeType = object : TypeToken<List<String>>() {}.type
        val participants: List<String> = gson.fromJson(request.body(), decodeType)
        cache.getIfPresent(key)?.let {
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

        response.status(HttpStatus.CREATED)
        return ""
    }

    private fun getSession(request: Request, response: Response, prefix: String? = null): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val cleanSessionID = sessionID.trim()
        val key = if (prefix != null) {
            "$prefix-session-$cleanSessionID"
        } else {
            "session-$cleanSessionID"
        }
        Timber.tag("server").d("get session %s", key)
        cache.getIfPresent(key)?.let {
            val session = it as? Session
            session?.let {
                response.status(HttpStatus.OK)
                response.type("application/json")
                return Gson().toJson(session.participants)
            } ?: response.status(HttpStatus.NOT_FOUND)
        } ?: response.status(HttpStatus.NOT_FOUND)
        return ""
    }

    private fun deleteSession(request: Request, response: Response): String {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatus.BAD_REQUEST)
            return ""
        }
        val cleanSessionID = sessionID.trim()
        val key = "session-$cleanSessionID"
        val keyStart = "$key-start"
        cache.invalidateAll(listOf(key, keyStart))
        return ""
    }

    fun stopServer() {
        try {
            this.service.stop()
            // clear cache
            cache.invalidateAll()
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