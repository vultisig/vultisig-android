package com.vultisig.wallet.data.models.agent

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// === Request Models ===

@Serializable
data class AgentSendMessageRequest(
    @SerialName("public_key") val publicKey: String,
    val content: String? = null,
    val model: String = "anthropic/claude-sonnet-4-5",
    val context: AgentMessageContext? = null,
    @SerialName("selected_suggestion_id") val selectedSuggestionId: String? = null,
    @SerialName("action_result") val actionResult: AgentActionResult? = null,
)

@Serializable
data class AgentMessageContext(
    @SerialName("vault_address") val vaultAddress: String? = null,
    @SerialName("vault_name") val vaultName: String? = null,
    val addresses: Map<String, String> = emptyMap(),
    val coins: List<AgentCoinInfo> = emptyList(),
    val balances: List<AgentBalanceInfo> = emptyList(),
    @SerialName("address_book") val addressBook: List<AgentAddressBookEntry> = emptyList(),
    @SerialName("all_vaults") val allVaults: List<AgentVaultInfo> = emptyList(),
    val instructions: String? = null,
)

@Serializable
data class AgentCoinInfo(
    val chain: String,
    val ticker: String,
    @SerialName("contract_address") val contractAddress: String? = null,
    @SerialName("is_native_token") val isNativeToken: Boolean,
    val decimals: Int,
)

@Serializable
data class AgentBalanceInfo(
    val chain: String,
    val asset: String,
    val symbol: String,
    val amount: String,
    val decimals: Int,
)

@Serializable
data class AgentAddressBookEntry(val title: String, val address: String, val chain: String)

@Serializable
data class AgentVaultInfo(
    val name: String,
    @SerialName("public_key_ecdsa") val publicKeyEcdsa: String,
)

@Serializable
data class AgentActionResult(
    val action: String,
    @SerialName("action_id") val actionId: String,
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class AgentGetStartersRequest(
    @SerialName("public_key") val publicKey: String,
    val context: AgentMessageContext? = null,
)

@Serializable
data class AgentCreateConversationRequest(@SerialName("public_key") val publicKey: String)

@Serializable
data class AgentListConversationsRequest(
    @SerialName("public_key") val publicKey: String,
    val skip: Int = 0,
    val take: Int = 50,
)

@Serializable
data class AgentGetConversationRequest(@SerialName("public_key") val publicKey: String)

// === Response Models ===

@Serializable
data class AgentConversation(
    val id: String,
    @SerialName("public_key") val publicKey: String,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class AgentBackendMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    val role: String,
    val content: String,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AgentConversationWithMessages(
    val id: String,
    @SerialName("public_key") val publicKey: String,
    val title: String? = null,
    val messages: List<AgentBackendMessage> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class AgentBackendAction(
    val id: String,
    val type: String,
    val title: String,
    val description: String? = null,
    val params: JsonElement? = null,
    @SerialName("auto_execute") val autoExecute: Boolean = false,
)

@Serializable
data class AgentBackendSuggestion(
    val id: String,
    @SerialName("plugin_id") val pluginId: String? = null,
    val title: String,
    val description: String? = null,
)

@Serializable
data class AgentTxReady(
    @SerialName("from_chain") val fromChain: String,
    @SerialName("from_symbol") val fromSymbol: String,
    @SerialName("to_chain") val toChain: String? = null,
    @SerialName("to_symbol") val toSymbol: String? = null,
    val amount: String,
    val sender: String,
    val destination: String,
    @SerialName("tx_type") val txType: String? = null,
    @SerialName("keysign_payload") val keysignPayload: String? = null,
    @SerialName("needs_approval") val needsApproval: Boolean = false,
    @SerialName("expected_output") val expectedOutput: String? = null,
    @SerialName("minimum_output") val minimumOutput: String? = null,
    val provider: String? = null,
)

@Serializable
data class AgentListConversationsResponse(
    val conversations: List<AgentConversation> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable data class AgentGetStartersResponse(val starters: List<String> = emptyList())

@Serializable
data class AgentSendMessageResponse(
    val message: AgentBackendMessage? = null,
    val title: String? = null,
    val suggestions: List<AgentBackendSuggestion>? = null,
    val actions: List<AgentBackendAction>? = null,
    @SerialName("tx_ready") val txReady: AgentTxReady? = null,
)

// === SSE Event Types ===

enum class AgentSSEEventType {
    TEXT_DELTA,
    TITLE,
    ACTIONS,
    SUGGESTIONS,
    TX_READY,
    MESSAGE,
    ERROR,
    DONE,
}

// === Chat UI Models ===

enum class AgentChatRole {
    User,
    Assistant,
}

enum class AgentToolCallStatus {
    RUNNING,
    SUCCESS,
    ERROR,
}

@Immutable
data class AgentToolCallInfo(
    val actionType: String,
    val title: String,
    val status: AgentToolCallStatus,
    val error: String? = null,
)

enum class AgentTxStatus {
    PENDING,
    CONFIRMED,
    FAILED,
}

@Immutable
data class AgentTxStatusInfo(
    val txHash: String,
    val chain: String,
    val status: AgentTxStatus,
    val label: String,
)

@Immutable
data class AgentChatMessage(
    val id: String,
    val role: AgentChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCall: AgentToolCallInfo? = null,
    val txStatus: AgentTxStatusInfo? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)
