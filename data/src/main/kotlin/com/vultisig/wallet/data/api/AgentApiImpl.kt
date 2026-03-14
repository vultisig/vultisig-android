package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.agent.AgentConversation
import com.vultisig.wallet.data.models.agent.AgentConversationWithMessages
import com.vultisig.wallet.data.models.agent.AgentCreateConversationRequest
import com.vultisig.wallet.data.models.agent.AgentGetConversationRequest
import com.vultisig.wallet.data.models.agent.AgentGetStartersRequest
import com.vultisig.wallet.data.models.agent.AgentGetStartersResponse
import com.vultisig.wallet.data.models.agent.AgentListConversationsRequest
import com.vultisig.wallet.data.models.agent.AgentListConversationsResponse
import com.vultisig.wallet.data.models.agent.AgentSendMessageRequest
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class AgentApiImpl
@Inject
constructor(private val http: HttpClient, @Named("sse") private val sseHttp: HttpClient) :
    AgentApi {

    override suspend fun createConversation(
        token: String,
        request: AgentCreateConversationRequest,
    ): AgentConversation =
        http
            .post("$BASE_URL/agent/conversations") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .bodyOrThrow()

    override suspend fun listConversations(
        token: String,
        request: AgentListConversationsRequest,
    ): AgentListConversationsResponse =
        http
            .post("$BASE_URL/agent/conversations/list") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .bodyOrThrow()

    override suspend fun getConversation(
        token: String,
        conversationId: String,
        request: AgentGetConversationRequest,
    ): AgentConversationWithMessages =
        http
            .post("$BASE_URL/agent/conversations/$conversationId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .bodyOrThrow()

    override suspend fun deleteConversation(token: String, conversationId: String) {
        val response =
            http.delete("$BASE_URL/agent/conversations/$conversationId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        if (!response.status.isSuccess()) {
            throw NetworkException(response.status.value, "DELETE failed: ${response.status}")
        }
    }

    override fun sendMessageStream(
        token: String,
        conversationId: String,
        request: AgentSendMessageRequest,
    ): Flow<AgentSSEEvent> = callbackFlow {
        sseHttp
            .preparePost("$BASE_URL/agent/conversations/$conversationId/messages") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .execute { response ->
                if (!response.status.isSuccess()) {
                    throw NetworkException(
                        response.status.value,
                        "SSE connection failed: ${response.status}",
                    )
                }

                val channel = response.bodyAsChannel()
                var currentEvent = ""
                var currentData = StringBuilder()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    when {
                        line.startsWith("event:") -> {
                            currentEvent = line.removePrefix("event:").trim()
                        }
                        line.startsWith("data:") -> {
                            // SSE spec: strip first space after colon
                            val payload = line.removePrefix("data:").removePrefix(" ")
                            if (currentData.isNotEmpty()) {
                                currentData.append('\n')
                            }
                            currentData.append(payload)
                        }
                        line.isBlank() && currentEvent.isNotEmpty() -> {
                            trySend(
                                AgentSSEEvent(event = currentEvent, data = currentData.toString())
                            )
                            currentEvent = ""
                            currentData = StringBuilder()
                        }
                    }
                }

                if (currentEvent.isNotEmpty() && currentData.isNotEmpty()) {
                    trySend(AgentSSEEvent(event = currentEvent, data = currentData.toString()))
                }
            }

        close()
        awaitClose()
    }

    override suspend fun getStarters(
        token: String,
        request: AgentGetStartersRequest,
    ): AgentGetStartersResponse =
        http
            .post("$BASE_URL/agent/starters") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .bodyOrThrow()

    companion object {
        private const val BASE_URL = "https://agent.vultisig.com"
    }
}
