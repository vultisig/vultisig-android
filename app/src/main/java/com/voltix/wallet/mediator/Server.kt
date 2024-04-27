package com.voltix.wallet.mediator

import android.util.Log
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import spark.Request
import spark.Response
import spark.Service
import spark.Spark.*

class Server {
    private val port = 18080
    private val cache: Cache<String, Any> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build()

    fun startMediator() {
        val httpService = Service.ignite()
        httpService.port(port)
        httpService.internalServerError("Internal Server Error")
        setupRouting(httpService)
        Log.d("Server", "Server started on port $port")
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
            getCompleteKeygen(
                request,
                response
            )
        }
        service.post("/complete/:sessionID") { request, response ->
            postCompleteKeygen(
                request,
                response
            )
        }
    }

    private fun postStartKeygenOrKeysign(request: Request, response: Response) {
        TODO("Not yet implemented")
    }

    private fun getCompleteKeygen(request: Request, response: Response) {
        TODO("Not yet implemented")
    }

    private fun getStartKeygenOrKeysign(request: Request, response: Response) {

    }

    private fun getCompleteKeySign(request: Request, response: Response) {
        response.body("complete")
        response.status()
    }

    private fun postCompleteKeySign(request: Request, response: Response) {

    }

    private fun deleteMessage(request: Request, response: Response) {

    }

    private fun postCompleteKeygen(request: Request, response: Response) {

    }

    private fun getMessage(request: Request, response: Response) {

    }

    private fun postMessage(request: Request, response: Response) {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.body("sessionID is empty")
            response.status(HttpStatus.BAD_REQUEST)
            return
        }
        val messageID = request.headers("message_id")

    }

    private fun postSession(
        request: Request,
        response: Response,
        prefix: String? = null,
    ) {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatus.BAD_REQUEST)
            return
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
    }

    private fun getSession(request: Request, response: Response, prefix: String? = null) {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatus.BAD_REQUEST)
            return
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
                response.body(Gson().toJson(session.participants))
                response.status(HttpStatus.OK)
            } ?: response.status(HttpStatus.NOT_FOUND)
        } ?: response.status(HttpStatus.NOT_FOUND)

    }

    private fun deleteSession(request: Request, response: Response) {
        val sessionID = request.params(":sessionID")
        sessionID ?: run {
            response.status(HttpStatus.BAD_REQUEST)
            return
        }
        val cleanSessionID = sessionID.trim()
        val key = "session-$cleanSessionID"
        val keyStart = "$key-start"
        cache.invalidateAll(listOf(key, keyStart))

    }

    fun stopServer() {
        stop()
        // clear cache
        cache.invalidateAll()
    }
}