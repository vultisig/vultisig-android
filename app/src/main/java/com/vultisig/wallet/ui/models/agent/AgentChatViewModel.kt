package com.vultisig.wallet.ui.models.agent

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.AgentApi
import com.vultisig.wallet.data.keygen.FastVaultKeysignHelper
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.agent.AgentChatMessage
import com.vultisig.wallet.data.models.agent.AgentChatRole
import com.vultisig.wallet.data.models.agent.AgentCreateConversationRequest
import com.vultisig.wallet.data.models.agent.AgentGetConversationRequest
import com.vultisig.wallet.data.models.agent.AgentGetStartersRequest
import com.vultisig.wallet.data.models.agent.AgentToolCallInfo
import com.vultisig.wallet.data.models.agent.AgentToolCallStatus
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.agent.AgentAuthService
import com.vultisig.wallet.data.usecases.agent.AgentContextBuilder
import com.vultisig.wallet.data.usecases.agent.AgentEvent
import com.vultisig.wallet.data.usecases.agent.AgentMessageProcessor
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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
    val isAuthenticating: Boolean = false,
    val showPasswordPrompt: Boolean = false,
    val showOverflowMenu: Boolean = false,
    val isVaultLoaded: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isFastVault: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
internal class AgentChatViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val agentApi: AgentApi,
    private val agentMessageProcessor: AgentMessageProcessor,
    private val agentAuthService: AgentAuthService,
    private val agentContextBuilder: AgentContextBuilder,
    private val fastVaultKeysignHelper: FastVaultKeysignHelper,
    private val vaultRepository: VaultRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentChatUiModel())
    val uiState: StateFlow<AgentChatUiModel> = _uiState.asStateFlow()

    private val navConversationId: String? = savedStateHandle.get<String>("conversationId")

    private var vault: Vault? = null
    private var isFirstMessage = true

    init {
        loadVaultAndInit()
        observeAgentEvents()
    }

    private fun loadVaultAndInit() {
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "AgentChat: Failed to load vault")
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to load vault: ${e.message}",
                        isVaultLoaded = false,
                    )
                }
            }
        ) {
            val vaultId = lastOpenedVaultRepository.lastOpenedVaultId.first()

            if (vaultId == null) {
                _uiState.update {
                    it.copy(
                        errorMessage = "No vault selected. Please select a vault first.",
                        isVaultLoaded = false,
                    )
                }
                return@safeLaunch
            }

            var loadedVault = vaultRepository.get(vaultId)
            if (loadedVault == null) {
                loadedVault = vaultRepository.getAll().firstOrNull()
            }
            vault = loadedVault

            if (loadedVault == null) {
                _uiState.update {
                    it.copy(
                        errorMessage = "No vault found. Please create a vault first.",
                        isVaultLoaded = false,
                    )
                }
                return@safeLaunch
            }

            // Check if we already have a valid token
            val existingToken = agentAuthService.getOrRefreshToken(loadedVault.pubKeyECDSA)
            val alreadyAuthed = existingToken != null

            _uiState.update {
                it.copy(
                    isVaultLoaded = true,
                    isAuthenticated = alreadyAuthed,
                    isFastVault = loadedVault.isFastVault(),
                )
            }

            if (navConversationId != null) {
                _uiState.update { it.copy(conversationId = navConversationId) }
                isFirstMessage = false
                loadConversationHistory(navConversationId)
            }

            if (alreadyAuthed) {
                loadStarters()
            }
        }
    }

    private suspend fun loadConversationHistory(conversationId: String) {
        val v = vault ?: return
        val token = agentAuthService.getOrRefreshToken(v.pubKeyECDSA) ?: return
        try {
            val conv =
                agentApi.getConversation(
                    token = token,
                    conversationId = conversationId,
                    request = AgentGetConversationRequest(publicKey = v.pubKeyECDSA),
                )
            val messages =
                conv.messages.map { msg ->
                    AgentChatMessage(
                        id = msg.id,
                        role =
                            if (msg.role == "user") AgentChatRole.User else AgentChatRole.Assistant,
                        content = msg.content,
                    )
                }
            val title =
                conv.title?.takeIf { it.isNotBlank() }
                    ?: agentMessageProcessor.getCachedTitle(conversationId)
                    ?: conv.messages
                        .firstOrNull { it.role == "user" }
                        ?.content
                        ?.take(MAX_TITLE_LENGTH)
            if (title != null) {
                agentMessageProcessor.setCachedTitle(conversationId, title)
            }
            _uiState.update { it.copy(messages = messages, conversationTitle = title) }
        } catch (_: Exception) {
            // Failed to load history, start fresh
        }
    }

    private fun loadStarters() {
        viewModelScope.safeLaunch {
            _uiState.update { it.copy(isLoadingStarters = true) }
            val v = vault
            val starters =
                if (v != null) {
                    val token = agentAuthService.getOrRefreshToken(v.pubKeyECDSA)
                    if (token != null) {
                        try {
                            val context = agentContextBuilder.buildFullContext(v)
                            val response =
                                agentApi.getStarters(
                                    token = token,
                                    request =
                                        AgentGetStartersRequest(
                                            publicKey = v.pubKeyECDSA,
                                            context = context,
                                        ),
                                )
                            response.starters.ifEmpty { FALLBACK_STARTERS }
                        } catch (_: Exception) {
                            FALLBACK_STARTERS
                        }
                    } else {
                        FALLBACK_STARTERS
                    }
                } else {
                    FALLBACK_STARTERS
                }
            _uiState.update { it.copy(starters = starters, isLoadingStarters = false) }
        }
    }

    private fun observeAgentEvents() {
        viewModelScope.launch {
            agentMessageProcessor.events.collect { event ->
                val currentConvId = _uiState.value.conversationId ?: return@collect
                handleAgentEvent(currentConvId, event)
            }
        }
    }

    private fun handleAgentEvent(currentConvId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> {
                if (event.conversationId != currentConvId) return
                appendOrUpdateStreamingMessage(event.delta)
            }

            is AgentEvent.TitleUpdated -> {
                if (event.conversationId != currentConvId) return
                _uiState.update { it.copy(conversationTitle = event.title) }
            }

            is AgentEvent.ToolCallStarted -> {
                if (event.conversationId != currentConvId) return
                val toolMsg =
                    AgentChatMessage(
                        id = event.actionId,
                        role = AgentChatRole.Assistant,
                        content = "",
                        toolCall =
                            AgentToolCallInfo(
                                actionType = event.actionType,
                                title = event.title,
                                status = AgentToolCallStatus.RUNNING,
                            ),
                    )
                _uiState.update { it.copy(messages = it.messages + toolMsg) }
            }

            is AgentEvent.ToolCallResult -> {
                if (event.conversationId != currentConvId) return
                _uiState.update { state ->
                    state.copy(
                        messages =
                            state.messages.map { msg ->
                                val tc = msg.toolCall
                                if (msg.id == event.actionId && tc != null) {
                                    msg.copy(
                                        toolCall =
                                            tc.copy(
                                                status =
                                                    if (event.success) AgentToolCallStatus.SUCCESS
                                                    else AgentToolCallStatus.ERROR,
                                                error = event.error,
                                            )
                                    )
                                } else msg
                            }
                    )
                }
            }

            is AgentEvent.Response -> {
                if (event.conversationId != currentConvId) return
                finalizeStreamingMessage(event.message)
            }

            is AgentEvent.Complete -> {
                if (event.conversationId != currentConvId) return
                finalizeStreamingMessage(event.message)
                _uiState.update { it.copy(isSending = false, isThinking = false) }
            }

            is AgentEvent.Error -> {
                if (event.conversationId != currentConvId) return
                addErrorMessage(event.error)
            }

            is AgentEvent.Loading -> {
                if (event.conversationId != currentConvId) return
                _uiState.update { it.copy(isThinking = true) }
            }

            is AgentEvent.AuthRequired -> {
                if (event.conversationId != currentConvId) return
                val v = vault
                if (v != null && v.isFastVault()) {
                    _uiState.update {
                        it.copy(showPasswordPrompt = true, isSending = false, isThinking = false)
                    }
                } else {
                    addErrorMessage(
                        "Vulti Agent requires a FastVault. Please use a FastVault to continue."
                    )
                }
            }

            is AgentEvent.TxReady -> {
                // TODO: Handle transaction ready state
            }
        }
    }

    private fun appendOrUpdateStreamingMessage(delta: String) {
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastMsg = messages.lastOrNull()
            if (lastMsg != null && lastMsg.role == AgentChatRole.Assistant && lastMsg.isStreaming) {
                messages[messages.lastIndex] = lastMsg.copy(content = lastMsg.content + delta)
            } else {
                messages.add(
                    AgentChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = AgentChatRole.Assistant,
                        content = delta,
                        isStreaming = true,
                    )
                )
            }
            state.copy(messages = messages, isThinking = false)
        }
    }

    private fun finalizeStreamingMessage(fullContent: String) {
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastMsg = messages.lastOrNull()
            if (lastMsg != null && lastMsg.role == AgentChatRole.Assistant && lastMsg.isStreaming) {
                if (fullContent.isNotBlank()) {
                    messages[messages.lastIndex] =
                        lastMsg.copy(content = fullContent, isStreaming = false)
                } else {
                    messages[messages.lastIndex] = lastMsg.copy(isStreaming = false)
                }
            } else if (fullContent.isNotBlank()) {
                messages.add(
                    AgentChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = AgentChatRole.Assistant,
                        content = fullContent,
                    )
                )
            }
            state.copy(messages = messages)
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

        val newTitle = _uiState.value.conversationTitle ?: message.take(MAX_TITLE_LENGTH)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputMessage = "",
                isSending = true,
                isThinking = true,
                conversationTitle = newTitle,
            )
        }
        // Cache the title so it shows in conversations list
        val convId = _uiState.value.conversationId
        if (convId != null) {
            agentMessageProcessor.setCachedTitle(convId, newTitle)
        }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "AgentChat: Failed to send message")
                addErrorMessage("Failed to send message: ${e.message}")
            }
        ) {
            val v = vault
            if (v == null) {
                addErrorMessage("No vault selected. Please go back and select a vault.")
                return@safeLaunch
            }

            val convId = ensureConversation(v)
            if (convId == null) {
                _uiState.update { it.copy(isSending = false, isThinking = false) }
                return@safeLaunch
            }

            agentMessageProcessor.processMessage(
                conversationId = convId,
                vault = v,
                message = message,
                isFirstMessage = isFirstMessage,
            )
            isFirstMessage = false
        }
    }

    private suspend fun ensureConversation(vault: Vault): String? {
        val existing = _uiState.value.conversationId
        if (existing != null) return existing

        val token = agentAuthService.getOrRefreshToken(vault.pubKeyECDSA)
        if (token == null) {
            if (vault.isFastVault()) {
                _uiState.update {
                    it.copy(showPasswordPrompt = true, isSending = false, isThinking = false)
                }
            } else {
                addErrorMessage(
                    "Vulti Agent requires a FastVault. Please use a FastVault to continue.",
                    resetSending = false,
                )
            }
            return null
        }

        return try {
            val conv =
                agentApi.createConversation(
                    token = token,
                    request = AgentCreateConversationRequest(publicKey = vault.pubKeyECDSA),
                )
            _uiState.update { it.copy(conversationId = conv.id) }
            // Cache any pending title for this new conversation
            val pendingTitle = _uiState.value.conversationTitle
            if (pendingTitle != null) {
                agentMessageProcessor.setCachedTitle(conv.id, pendingTitle)
            }
            conv.id
        } catch (e: Exception) {
            addErrorMessage("Failed to create conversation: ${e.message}", resetSending = false)
            null
        }
    }

    fun onPasswordSubmit(password: String) {
        _uiState.update { it.copy(showPasswordPrompt = false, isAuthenticating = true) }

        viewModelScope.safeLaunch {
            val v =
                vault
                    ?: run {
                        _uiState.update { it.copy(isAuthenticating = false) }
                        return@safeLaunch
                    }

            try {
                agentAuthService.signIn(v) { messageHash ->
                    fastVaultKeysignHelper.sign(v, password, messageHash)
                }
                _uiState.update { it.copy(isAuthenticating = false, isAuthenticated = true) }

                loadStarters()

                // Retry sending the pending message if any
                val pendingMessage =
                    _uiState.value.messages.lastOrNull { it.role == AgentChatRole.User }?.content
                if (pendingMessage != null) {
                    _uiState.update { it.copy(isSending = true, isThinking = true) }
                    val convId = ensureConversation(v) ?: return@safeLaunch
                    agentMessageProcessor.processMessage(
                        conversationId = convId,
                        vault = v,
                        message = pendingMessage,
                        isFirstMessage = isFirstMessage,
                    )
                    isFirstMessage = false
                }
            } catch (e: Exception) {
                Timber.e(e, "AgentChat: Sign-in failed")
                addErrorMessage("Authentication failed: ${e.message}")
            }
        }
    }

    fun onAuthorizeClick() {
        val v = vault ?: return
        if (v.isFastVault()) {
            _uiState.update { it.copy(showPasswordPrompt = true) }
        }
    }

    fun onPasswordDismiss() {
        _uiState.update {
            it.copy(showPasswordPrompt = false, isSending = false, isThinking = false)
        }
        viewModelScope.launch { navigator.back() }
    }

    fun onStarterClick(starter: String) {
        _uiState.update { it.copy(inputMessage = starter) }
        onSendMessage()
    }

    fun onOverflowMenuToggle() {
        _uiState.update { it.copy(showOverflowMenu = !it.showOverflowMenu) }
    }

    fun onOverflowMenuDismiss() {
        _uiState.update { it.copy(showOverflowMenu = false) }
    }

    fun onNewChatClick() {
        _uiState.update { it.copy(showOverflowMenu = false) }
        viewModelScope.launch { navigator.route(Route.AgentChat()) }
    }

    fun onGiveFeedbackClick() {
        _uiState.update { it.copy(showOverflowMenu = false) }
        // TODO: Navigate to feedback screen
    }

    fun onDeleteConversation() {
        _uiState.update { it.copy(showOverflowMenu = false) }
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.safeLaunch {
            val v = vault ?: return@safeLaunch
            val token = agentAuthService.getOrRefreshToken(v.pubKeyECDSA) ?: return@safeLaunch
            try {
                agentApi.deleteConversation(token, convId)
                navigator.back()
            } catch (e: Exception) {
                Timber.e(e, "AgentChat: Failed to delete conversation")
            }
        }
    }

    fun onHistoryClick() {
        _uiState.update { it.copy(showOverflowMenu = false) }
        viewModelScope.launch { navigator.route(Route.AgentConversations) }
    }

    fun onBackClick() {
        viewModelScope.launch { navigator.back() }
    }

    private fun addErrorMessage(content: String, resetSending: Boolean = true) {
        val errorMsg =
            AgentChatMessage(
                id = UUID.randomUUID().toString(),
                role = AgentChatRole.Assistant,
                content = content,
                isError = true,
            )
        _uiState.update {
            it.copy(
                messages = it.messages + errorMsg,
                isSending = if (resetSending) false else it.isSending,
                isThinking = if (resetSending) false else it.isThinking,
                isAuthenticating = false,
            )
        }
    }

    companion object {
        private const val MAX_TITLE_LENGTH = 50

        private val FALLBACK_STARTERS =
            listOf(
                "Show me plugins and what they can do",
                "I want to earn APY on BTC",
                "Send amount to...",
                "Prepare a swap from ETH to BTC",
            )
    }
}
