package com.voltix.wallet.mediator

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import spark.Request
import spark.Response
import spark.Service
import spark.Spark.stop

class Server(context: Context) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val port = 18080
    private val cache: Cache<String, Any> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build()
    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            val serviceName = serviceInfo.serviceName
            Log.d("Server", "Service registered: $serviceName")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("Server", "Service registration failed: $errorCode")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            Log.d("Server", "Service unregistered: ${serviceInfo.serviceName}")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("Server", "Service unregistration failed: $errorCode")
        }
    }

    fun startMediator(name: String) {
        val httpService = Service.ignite()
        httpService.port(port)
        httpService.internalServerError("Internal Server Error")
        setupRouting(httpService)
        registerService(name)
        Log.d("Server", "Server started on port $port")
    }

    private fun registerService(name: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = "_http._tcp"
        }
        serviceInfo.port = port
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
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
        val message = Message.fromJson(request.body())
        for (recipient in message.to) {
            val key = messageID?.let {
                "${sessionID.trim()}-$recipient-$it-${message.hash}"
            } ?: run {
                "$sessionID-$recipient-${message.hash}"
            }
            Log.d("server", "put message $key")
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
        Log.d("server", "body: ${request.body()}")
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
        Log.d("server", "get session $key")
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
        stop()
        // clear cache
        cache.invalidateAll()
        nsdManager.unregisterService(registrationListener)
    }
}