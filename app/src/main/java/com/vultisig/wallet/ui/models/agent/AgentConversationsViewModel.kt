package com.vultisig.wallet.ui.models.agent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.AgentApi
import com.vultisig.wallet.data.models.agent.AgentListConversationsRequest
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.agent.AgentAuthService
import com.vultisig.wallet.data.usecases.agent.AgentMessageProcessor
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable data class ConversationItemUiModel(val id: String, val title: String)

@Immutable
data class AgentConversationsUiModel(
    val conversations: List<ConversationItemUiModel> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
internal class AgentConversationsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val agentApi: AgentApi,
    private val agentAuthService: AgentAuthService,
    private val agentMessageProcessor: AgentMessageProcessor,
    private val vaultRepository: VaultRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentConversationsUiModel())
    val uiState: StateFlow<AgentConversationsUiModel> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.safeLaunch {
            _uiState.update { it.copy(isLoading = true) }

            val vaultId = lastOpenedVaultRepository.lastOpenedVaultId.first()
            val vault = vaultId?.let { vaultRepository.get(it) }

            if (vault != null) {
                val token = agentAuthService.getOrRefreshToken(vault.pubKeyECDSA)
                if (token != null) {
                    try {
                        val response =
                            agentApi.listConversations(
                                token = token,
                                request =
                                    AgentListConversationsRequest(publicKey = vault.pubKeyECDSA),
                            )
                        val items =
                            response.conversations.map { conv ->
                                ConversationItemUiModel(
                                    id = conv.id,
                                    title =
                                        conv.title?.takeIf { it.isNotBlank() }
                                            ?: agentMessageProcessor.getCachedTitle(conv.id)
                                            ?: "New conversation",
                                )
                            }
                        _uiState.update { it.copy(conversations = items, isLoading = false) }
                        return@safeLaunch
                    } catch (_: Exception) {
                        // Fall through to empty state
                    }
                }
            }

            _uiState.update { it.copy(conversations = emptyList(), isLoading = false) }
        }
    }

    fun onConversationClick(conversationId: String) {
        viewModelScope.launch { navigator.route(Route.AgentChat(conversationId = conversationId)) }
    }

    fun onNewChatClick() {
        viewModelScope.launch { navigator.route(Route.AgentChat()) }
    }

    fun onBackClick() {
        viewModelScope.launch { navigator.back() }
    }
}
