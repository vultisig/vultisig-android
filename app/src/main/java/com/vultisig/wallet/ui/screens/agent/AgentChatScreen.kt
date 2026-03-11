package com.vultisig.wallet.ui.screens.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.agent.AgentChatMessage
import com.vultisig.wallet.data.models.agent.AgentChatRole
import com.vultisig.wallet.data.models.agent.AgentToolCallInfo
import com.vultisig.wallet.data.models.agent.AgentToolCallStatus
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.agent.AgentChatUiModel
import com.vultisig.wallet.ui.models.agent.AgentChatViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AgentChatScreen(viewModel: AgentChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    AgentChatContent(
        state = state,
        onMessageChange = viewModel::onMessageChange,
        onSendClick = viewModel::onSendMessage,
        onStarterClick = viewModel::onStarterClick,
        onHistoryClick = viewModel::onHistoryClick,
    )
}

@Composable
private fun AgentChatContent(
    state: AgentChatUiModel,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStarterClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    val bgPrimary = Theme.v2.colors.backgrounds.primary

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(bgPrimary)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height * 0.15f)
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0xFF1A3A6E).copy(alpha = 0.6f),
                                        Color(0xFF0D2247).copy(alpha = 0.3f),
                                        Color.Transparent,
                                    ),
                                center = center,
                                radius = size.width * 0.7f,
                            ),
                        center = center,
                        radius = size.width * 0.7f,
                    )
                }
                .imePadding()
    ) {
        // Top bar
        AgentTopBar(title = state.conversationTitle, onHistoryClick = onHistoryClick)

        // Messages or empty state
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                AgentEmptyState(
                    starters = state.starters,
                    isLoadingStarters = state.isLoadingStarters,
                    onStarterClick = onStarterClick,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(items = state.messages, key = { it.id }) { message ->
                        AgentChatMessageItem(message = message)
                    }

                    if (state.isThinking) {
                        item { AgentThinkingIndicator() }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }

        // Input bar
        AgentInputBar(
            message = state.inputMessage,
            isLoading = state.isSending,
            onMessageChange = onMessageChange,
            onSendClick = onSendClick,
        )
    }
}

@Composable
private fun AgentTopBar(title: String?, onHistoryClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHistoryClick) {
            Icon(
                painter = painterResource(R.drawable.hamburger_menu),
                contentDescription = null,
                tint = Theme.v2.colors.text.primary,
            )
        }

        Text(
            text = title ?: stringResource(R.string.agent_title),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = { /* overflow menu */ }) {
            Icon(
                painter = painterResource(R.drawable.ic_three_dots),
                contentDescription = null,
                tint = Theme.v2.colors.text.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEmptyState(
    starters: List<String>,
    isLoadingStarters: Boolean,
    onStarterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Glowing orb placeholder
        Box(
            modifier =
                Modifier.size(80.dp).drawBehind {
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(Color(0xFF3B82F6), Color(0xFF1E40AF), Color.Transparent),
                                radius = size.width,
                            ),
                        radius = size.width * 0.4f,
                    )
                }
        )

        UiSpacer(size = 24.dp)

        Text(
            text = stringResource(R.string.agent_welcome_title),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(size = 8.dp)

        Text(
            text = stringResource(R.string.agent_welcome_subtitle),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )

        UiSpacer(size = 32.dp)

        AnimatedVisibility(
            visible = starters.isNotEmpty() && !isLoadingStarters,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                starters.forEach { starter ->
                    AgentStarterChip(text = starter, onClick = { onStarterClick(starter) })
                }
            }
        }
    }
}

@Composable
private fun AgentStarterChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SuggestionChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border =
            SuggestionChipDefaults.suggestionChipBorder(
                enabled = true,
                borderColor = Theme.v2.colors.border.normal,
            ),
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = Theme.v2.colors.backgrounds.secondary
            ),
    )
}

@Composable
private fun AgentChatMessageItem(message: AgentChatMessage, modifier: Modifier = Modifier) {
    val toolCall = message.toolCall
    when {
        toolCall != null -> {
            AgentToolCallRow(toolCall = toolCall, modifier = modifier)
        }
        else -> {
            val isUser = message.role == AgentChatRole.User
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
                Box(
                    modifier =
                        Modifier.widthIn(max = 300.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp,
                                )
                            )
                            .background(
                                if (isUser) Color(0xFF0B4EFF)
                                else Theme.v2.colors.backgrounds.secondary
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentToolCallRow(toolCall: AgentToolCallInfo, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (toolCall.status) {
            AgentToolCallStatus.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Theme.v2.colors.text.secondary,
                )
            }
            AgentToolCallStatus.SUCCESS -> {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Theme.v2.colors.alerts.success,
                    modifier = Modifier.size(16.dp),
                )
            }
            AgentToolCallStatus.ERROR -> {
                Icon(
                    painter = painterResource(R.drawable.close_2),
                    contentDescription = null,
                    tint = Theme.v2.colors.alerts.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = toolCall.title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
private fun AgentThinkingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "thinking")

        repeat(3) { index ->
            val alpha by
                infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200),
                        ),
                    label = "dot_$index",
                )

            Box(
                modifier =
                    Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.text.secondary.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun AgentInputBar(
    message: String,
    isLoading: Boolean,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(R.string.agent_input_placeholder),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            },
            textStyle = Theme.brockmann.body.s.medium.copy(color = Theme.v2.colors.text.primary),
            colors =
                TextFieldDefaults.colors(
                    unfocusedContainerColor = Theme.v2.colors.backgrounds.secondary,
                    focusedContainerColor = Theme.v2.colors.backgrounds.secondary,
                    cursorColor = Theme.v2.colors.buttons.ctaPrimary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                ),
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendClick() }),
            singleLine = false,
            maxLines = 4,
        )

        IconButton(
            onClick = onSendClick,
            enabled = message.isNotBlank() && !isLoading,
            modifier =
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isNotBlank() && !isLoading) Theme.v2.colors.buttons.ctaPrimary
                        else Theme.v2.colors.backgrounds.secondary
                    ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Theme.v2.colors.text.primary,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.send),
                    contentDescription = stringResource(R.string.agent_send),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
