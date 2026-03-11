package com.vultisig.wallet.ui.models.agent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.agent.AgentChatMessage
import com.vultisig.wallet.data.models.agent.AgentChatRole
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AgentChatUiModel(
    val conversationId: String? = null,
    val conversationTitle: String? = null,
    val messages: List<AgentChatMessage> = emptyList(),
    val inputMessage: String = "",
    val isSending: Boolean = false,
    val isThinking: Boolean = false,
    val starters: List<String> = emptyList(),
    val isLoadingStarters: Boolean = false,
)

@HiltViewModel
internal class AgentChatViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val navigator: Navigator<Destination>) :
    ViewModel() {

    private val _uiState = MutableStateFlow(AgentChatUiModel())
    val uiState: StateFlow<AgentChatUiModel> = _uiState.asStateFlow()

    private val conversationId: String? = savedStateHandle.get<String>("conversationId")

    init {
        loadStarters()
    }

    private fun loadStarters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStarters = true) }
            // Hardcoded fallback starters for now
            val fallbackStarters =
                listOf(
                    "Show me plugins and what they can do",
                    "I want to earn APY on BTC",
                    "Send amount to...",
                    "Prepare a swap from ETH to BTC",
                )
            _uiState.update { it.copy(starters = fallbackStarters, isLoadingStarters = false) }
        }
    }

    fun onMessageChange(message: String) {
        _uiState.update { it.copy(inputMessage = message) }
    }

    fun onSendMessage() {
        val message = _uiState.value.inputMessage.trim()
        if (message.isBlank()) return

        val userMessage =
            AgentChatMessage(
                id = UUID.randomUUID().toString(),
                role = AgentChatRole.User,
                content = message,
            )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputMessage = "",
                isSending = true,
                isThinking = true,
            )
        }

        viewModelScope.launch {
            // TODO: Integrate with AgentApi for real backend communication
            // For now, simulate a response
            delay(1500)

            val assistantMessage =
                AgentChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = AgentChatRole.Assistant,
                    content =
                        "I'm Vulti Agent, your in-app assistant. I can help you with swaps, transfers, plugin management, and more. This feature is currently being set up — full functionality coming soon.",
                )

            _uiState.update {
                it.copy(
                    messages = it.messages + assistantMessage,
                    isSending = false,
                    isThinking = false,
                )
            }
        }
    }

    fun onStarterClick(starter: String) {
        _uiState.update { it.copy(inputMessage = starter) }
        onSendMessage()
    }

    fun onHistoryClick() {
        viewModelScope.launch { navigator.route(Route.AgentConversations) }
    }
}
