package com.vultisig.wallet.data.usecases.agent

import com.vultisig.wallet.data.api.AgentApi
import com.vultisig.wallet.data.api.AgentSSEEvent
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.agent.AgentActionResult
import com.vultisig.wallet.data.models.agent.AgentBackendAction
import com.vultisig.wallet.data.models.agent.AgentBackendSuggestion
import com.vultisig.wallet.data.models.agent.AgentSSEEventType
import com.vultisig.wallet.data.models.agent.AgentSendMessageRequest
import com.vultisig.wallet.data.models.agent.AgentSendMessageResponse
import com.vultisig.wallet.data.models.agent.AgentTxReady
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

sealed interface AgentEvent {
    data class TextDelta(val conversationId: String, val delta: String) : AgentEvent

    data class TitleUpdated(val conversationId: String, val title: String) : AgentEvent

    data class ToolCallStarted(
        val conversationId: String,
        val actionId: String,
        val actionType: String,
        val title: String,
    ) : AgentEvent

    data class ToolCallResult(
        val conversationId: String,
        val actionId: String,
        val actionType: String,
        val success: Boolean,
        val error: String? = null,
    ) : AgentEvent

    data class Response(
        val conversationId: String,
        val message: String,
        val actions: List<AgentBackendAction> = emptyList(),
        val suggestions: List<AgentBackendSuggestion> = emptyList(),
    ) : AgentEvent

    data class TxReady(val conversationId: String, val txReady: AgentTxReady) : AgentEvent

    data class Complete(val conversationId: String, val message: String) : AgentEvent

    data class Error(val conversationId: String, val error: String) : AgentEvent

    data class Loading(val conversationId: String) : AgentEvent

    data class AuthRequired(val conversationId: String, val vaultPubKey: String) : AgentEvent
}

@Singleton
class AgentMessageProcessor
@Inject
constructor(
    private val agentApi: AgentApi,
    private val contextBuilder: AgentContextBuilder,
    private val authService: AgentAuthService,
    private val toolExecutor: AgentToolExecutor,
    private val json: Json,
) {

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val _conversationTitles = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getCachedTitle(conversationId: String): String? = _conversationTitles[conversationId]

    fun setCachedTitle(conversationId: String, title: String) {
        _conversationTitles[conversationId] = title
    }

    suspend fun processMessage(
        conversationId: String,
        vault: Vault,
        message: String,
        isFirstMessage: Boolean,
    ) {
        val token = authService.getOrRefreshToken(vault.pubKeyECDSA)
        if (token == null) {
            _events.emit(AgentEvent.AuthRequired(conversationId, vault.pubKeyECDSA))
            return
        }

        _events.emit(AgentEvent.Loading(conversationId))

        val context =
            if (isFirstMessage) {
                contextBuilder.buildFullContext(vault)
            } else {
                contextBuilder.buildLightContext(vault)
            }

        val request =
            AgentSendMessageRequest(
                publicKey = vault.pubKeyECDSA,
                content = message,
                context = context,
            )

        try {
            val response = collectSSEResponse(token, conversationId, request)
            handleBackendResponse(conversationId, vault, token, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleError(conversationId, vault.pubKeyECDSA, e)
        }
    }

    suspend fun reportActionResult(
        conversationId: String,
        vault: Vault,
        result: AgentActionResult,
    ) {
        val token = authService.getOrRefreshToken(vault.pubKeyECDSA) ?: return
        val context = contextBuilder.buildLightContext(vault)

        val request =
            AgentSendMessageRequest(
                publicKey = vault.pubKeyECDSA,
                actionResult = result,
                context = context,
            )

        try {
            val response = collectSSEResponse(token, conversationId, request)
            handleBackendResponse(conversationId, vault, token, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleError(conversationId, vault.pubKeyECDSA, e)
        }
    }

    private suspend fun collectSSEResponse(
        token: String,
        conversationId: String,
        request: AgentSendMessageRequest,
    ): SSEResponse {
        val response = SSEResponse()

        agentApi.sendMessageStream(token, conversationId, request).collect { event ->
            processSSEEvent(conversationId, event, response)
        }

        return response
    }

    private suspend fun processSSEEvent(
        conversationId: String,
        event: AgentSSEEvent,
        response: SSEResponse,
    ) {
        val eventType =
            try {
                AgentSSEEventType.valueOf(event.event.uppercase())
            } catch (_: IllegalArgumentException) {
                return
            }

        when (eventType) {
            AgentSSEEventType.TEXT_DELTA -> {
                val delta =
                    try {
                        json
                            .parseToJsonElement(event.data)
                            .jsonObject["delta"]
                            ?.jsonPrimitive
                            ?.content ?: event.data
                    } catch (_: Exception) {
                        event.data
                    }
                response.textContent.append(delta)
                _events.emit(AgentEvent.TextDelta(conversationId, delta))
            }

            AgentSSEEventType.TITLE -> {
                val title =
                    try {
                        json
                            .parseToJsonElement(event.data)
                            .jsonObject["title"]
                            ?.jsonPrimitive
                            ?.content ?: event.data.trim()
                    } catch (_: Exception) {
                        event.data.trim()
                    }
                if (title.isNotBlank()) {
                    response.title = title
                    _conversationTitles[conversationId] = title
                    _events.emit(AgentEvent.TitleUpdated(conversationId, title))
                }
            }

            AgentSSEEventType.ACTIONS -> {
                try {
                    val wrapper = json.decodeFromString<AgentSendMessageResponse>(event.data)
                    val actions = wrapper.actions.orEmpty()
                    response.actions.addAll(actions)
                } catch (_: Exception) {
                    try {
                        val actions = json.decodeFromString<List<AgentBackendAction>>(event.data)
                        response.actions.addAll(actions)
                    } catch (_: Exception) {}
                }
            }

            AgentSSEEventType.SUGGESTIONS -> {
                try {
                    val wrapper = json.decodeFromString<AgentSendMessageResponse>(event.data)
                    val suggestions = wrapper.suggestions.orEmpty()
                    response.suggestions.addAll(suggestions)
                } catch (_: Exception) {
                    try {
                        val suggestions =
                            json.decodeFromString<List<AgentBackendSuggestion>>(event.data)
                        response.suggestions.addAll(suggestions)
                    } catch (_: Exception) {}
                }
            }

            AgentSSEEventType.TX_READY -> {
                try {
                    val wrapper = json.decodeFromString<AgentSendMessageResponse>(event.data)
                    val txReady = wrapper.txReady
                    if (txReady != null) {
                        response.txReady = txReady
                        _events.emit(AgentEvent.TxReady(conversationId, txReady))
                    }
                } catch (_: Exception) {
                    try {
                        val txReady = json.decodeFromString<AgentTxReady>(event.data)
                        response.txReady = txReady
                        _events.emit(AgentEvent.TxReady(conversationId, txReady))
                    } catch (_: Exception) {}
                }
            }

            AgentSSEEventType.MESSAGE -> {
                try {
                    val wrapper = json.decodeFromString<AgentSendMessageResponse>(event.data)
                    val content = wrapper.message?.content.orEmpty()
                    if (content.isNotBlank()) {
                        response.textContent.clear()
                        response.textContent.append(content)
                    }
                } catch (_: Exception) {
                    try {
                        val msgJson = json.parseToJsonElement(event.data).jsonObject
                        val content = msgJson["content"]?.jsonPrimitive?.content.orEmpty()
                        if (content.isNotBlank()) {
                            response.textContent.clear()
                            response.textContent.append(content)
                        }
                    } catch (_: Exception) {}
                }
            }

            AgentSSEEventType.ERROR -> {
                val errorMsg =
                    try {
                        json
                            .parseToJsonElement(event.data)
                            .jsonObject["error"]
                            ?.jsonPrimitive
                            ?.content ?: event.data.trim()
                    } catch (_: Exception) {
                        event.data.trim()
                    }
                response.error = errorMsg
                _events.emit(AgentEvent.Error(conversationId, errorMsg))
            }

            AgentSSEEventType.DONE -> {
                // stream complete
            }
        }
    }

    private suspend fun handleBackendResponse(
        conversationId: String,
        vault: Vault,
        token: String,
        response: SSEResponse,
    ) {
        if (response.error != null) return

        val message = response.textContent.toString()
        val allActions = response.actions

        val (unprotectedActions, protectedActions) = filterProtectedActions(allActions)
        val nonAutoActions = filterNonAutoActions(unprotectedActions)

        _events.emit(
            AgentEvent.Response(
                conversationId = conversationId,
                message = message,
                actions = nonAutoActions,
                suggestions = response.suggestions,
            )
        )

        val autoActions = filterAutoActions(unprotectedActions)
        val (actionsAfterBuild, buildAction) = filterBuildTx(autoActions)
        val (actionsToExecute, signAction) = filterSignTx(actionsAfterBuild)

        val results = mutableListOf<AgentActionResult>()
        for (action in actionsToExecute) {
            _events.emit(
                AgentEvent.ToolCallStarted(
                    conversationId = conversationId,
                    actionId = action.id,
                    actionType = action.type,
                    title = action.title,
                )
            )

            val result = executeToolLocally(action, vault)

            _events.emit(
                AgentEvent.ToolCallResult(
                    conversationId = conversationId,
                    actionId = action.id,
                    actionType = action.type,
                    success = result.success,
                    error = result.error,
                )
            )

            results.add(result)
        }

        if (results.isNotEmpty()) {
            reportBatchResults(conversationId, vault, token, results)
            return
        }

        // TODO: Handle signAction + buildAction (tx pipeline)
        // TODO: Handle protectedActions (password/confirmation prompts)

        if (protectedActions.isEmpty() && buildAction == null && signAction == null) {
            _events.emit(AgentEvent.Complete(conversationId, message))
        }
    }

    private suspend fun reportBatchResults(
        conversationId: String,
        vault: Vault,
        token: String,
        results: List<AgentActionResult>,
    ) {
        val context = contextBuilder.buildLightContext(vault)

        for (i in results.indices) {
            val request =
                AgentSendMessageRequest(
                    publicKey = vault.pubKeyECDSA,
                    actionResult = results[i],
                    context = context,
                )

            if (i == results.lastIndex) {
                try {
                    val resp = collectSSEResponse(token, conversationId, request)
                    handleBackendResponse(conversationId, vault, token, resp)
                } catch (e: Exception) {
                    Timber.w(e, "AgentProcessor: Failed to report last action result")
                }
            } else {
                try {
                    agentApi.sendMessageStream(token, conversationId, request).collect {}
                } catch (e: Exception) {
                    Timber.w(e, "AgentProcessor: Failed to report action result")
                }
            }
        }
    }

    private suspend fun executeToolLocally(
        action: AgentBackendAction,
        vault: Vault,
    ): AgentActionResult =
        try {
            toolExecutor.execute(action, vault)
        } catch (e: Exception) {
            Timber.e(e, "AgentTool: Failed to execute ${action.type}")
            AgentActionResult(
                action = action.type,
                actionId = action.id,
                success = false,
                error = e.message ?: "Unknown error",
            )
        }

    private suspend fun handleError(conversationId: String, vaultPubKey: String, error: Exception) {
        val message = error.message ?: error.toString()
        if (message.contains("401") || message.contains("Unauthorized", ignoreCase = true)) {
            authService.invalidateToken(vaultPubKey)
            _events.emit(AgentEvent.AuthRequired(conversationId, vaultPubKey))
        } else {
            _events.emit(AgentEvent.Error(conversationId, message))
        }
    }

    private class SSEResponse {
        val textContent = StringBuilder()
        var title: String? = null
        val actions = mutableListOf<AgentBackendAction>()
        val suggestions = mutableListOf<AgentBackendSuggestion>()
        var txReady: AgentTxReady? = null
        var error: String? = null
    }
}
