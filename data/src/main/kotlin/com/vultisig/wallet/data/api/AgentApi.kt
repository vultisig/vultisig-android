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
import kotlinx.coroutines.flow.Flow

internal interface AgentApi {

    suspend fun createConversation(
        token: String,
        request: AgentCreateConversationRequest,
    ): AgentConversation

    suspend fun listConversations(
        token: String,
        request: AgentListConversationsRequest,
    ): AgentListConversationsResponse

    suspend fun getConversation(
        token: String,
        conversationId: String,
        request: AgentGetConversationRequest,
    ): AgentConversationWithMessages

    suspend fun deleteConversation(token: String, conversationId: String)

    fun sendMessageStream(
        token: String,
        conversationId: String,
        request: AgentSendMessageRequest,
    ): Flow<AgentSSEEvent>

    suspend fun getStarters(
        token: String,
        request: AgentGetStartersRequest,
    ): AgentGetStartersResponse
}

data class AgentSSEEvent(val event: String, val data: String)
