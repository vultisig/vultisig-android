package com.vultisig.wallet.ui.models.agent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
constructor(private val navigator: Navigator<Destination>) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentConversationsUiModel())
    val uiState: StateFlow<AgentConversationsUiModel> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // TODO: Load from AgentApi when auth is implemented
            // For now, show empty state
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
