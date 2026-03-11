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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class AgentApiImpl @Inject constructor(private val http: HttpClient) : AgentApi {

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
            .body()

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
            .body()

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
            .body()

    override suspend fun deleteConversation(token: String, conversationId: String) {
        http.delete("$BASE_URL/agent/conversations/$conversationId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    override fun sendMessageStream(
        token: String,
        conversationId: String,
        request: AgentSendMessageRequest,
    ): Flow<AgentSSEEvent> = flow {
        http
            .preparePost("$BASE_URL/agent/conversations/$conversationId/messages") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            .execute { response ->
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
                            currentData.append(line.removePrefix("data:").trim())
                        }
                        line.isBlank() && currentEvent.isNotEmpty() -> {
                            emit(AgentSSEEvent(event = currentEvent, data = currentData.toString()))
                            currentEvent = ""
                            currentData = StringBuilder()
                        }
                    }
                }

                // Emit any remaining event
                if (currentEvent.isNotEmpty() && currentData.isNotEmpty()) {
                    emit(AgentSSEEvent(event = currentEvent, data = currentData.toString()))
                }
            }
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
            .body()

    companion object {
        private const val BASE_URL = "https://agent.vultisig.com"
    }
}
