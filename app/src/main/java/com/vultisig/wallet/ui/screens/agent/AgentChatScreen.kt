package com.vultisig.wallet.ui.screens.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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

private val ScreenBg = Color(0xFF02122B)
private val Surface1 = Color(0xFF061B3A)
private val BorderLight = Color(0xFF11284A)
private val CtaPrimary = Color(0xFF0B4EFF)
private val CtaBorder = Color(0xFF2155DF)
private val InfoLight = Color(0xFF5CA7FF)

@Composable
internal fun AgentChatScreen(viewModel: AgentChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    AgentChatContent(
        state = state,
        onMessageChange = viewModel::onMessageChange,
        onSendClick = viewModel::onSendMessage,
        onStarterClick = viewModel::onStarterClick,
        onMenuClick = viewModel::onHistoryClick,
        onOverflowClick = viewModel::onOverflowMenuToggle,
        onOverflowDismiss = viewModel::onOverflowMenuDismiss,
        onGiveFeedbackClick = viewModel::onGiveFeedbackClick,
        onDeleteClick = viewModel::onDeleteConversation,
        onSessionsClick = viewModel::onHistoryClick,
        onPasswordSubmit = viewModel::onPasswordSubmit,
        onCloseClick = viewModel::onPasswordDismiss,
    )
}

@Composable
private fun AgentChatContent(
    state: AgentChatUiModel,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStarterClick: (String) -> Unit,
    onMenuClick: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onGiveFeedbackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSessionsClick: () -> Unit,
    onPasswordSubmit: (String) -> Unit,
    onCloseClick: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isThinking) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ScreenBg).imePadding()) {
        // Top bar — show title only when there's a conversation or a title to display
        val showTitle = state.conversationTitle != null || state.conversationId != null
        AgentTopBar(
            title = if (showTitle) state.conversationTitle else null,
            showTitle = showTitle,
            showHamburger = state.isAuthenticated,
            showOverflowMenu = state.showOverflowMenu,
            hasConversation = state.conversationId != null,
            onMenuClick = onMenuClick,
            onOverflowClick = onOverflowClick,
            onOverflowDismiss = onOverflowDismiss,
            onGiveFeedbackClick = onGiveFeedbackClick,
            onDeleteClick = onDeleteClick,
        )

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!state.isVaultLoaded) {
                // Still loading vault / checking token — show nothing to avoid flash
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = CtaPrimary,
                    )
                }
            } else if (!state.isAuthenticated && state.messages.isEmpty()) {
                AgentWelcomeState(
                    isAuthenticating = state.isAuthenticating,
                    errorMessage = state.errorMessage,
                )
            } else if (state.messages.isEmpty() && !state.isThinking) {
                AgentEmptyState(
                    starters = state.starters,
                    isLoadingStarters = state.isLoadingStarters,
                    errorMessage = state.errorMessage,
                    onStarterClick = onStarterClick,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(items = state.messages, key = { it.id }) { message ->
                        AgentChatMessageItem(message = message)
                    }
                    if (state.isThinking) {
                        item(key = "thinking") { AgentThinkingIndicator() }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }

        // Bottom bar — depends on auth state (hidden until vault loaded)
        if (!state.isVaultLoaded) {
            // Don't show any input bar while loading
        } else if (!state.isAuthenticated && state.isFastVault) {
            // Unauthenticated FastVault: "Authorize Agent" label + password input
            Column {
                Text(
                    text = stringResource(R.string.agent_authorize),
                    style = Theme.brockmann.supplementary.caption,
                    color = InfoLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                AgentPasswordInputBar(onPasswordSubmit = onPasswordSubmit, onDismiss = onCloseClick)
            }
        } else if (!state.isAuthenticated) {
            // Non-FastVault: info panel + close button
            AgentNonFastVaultBar(onCloseClick = onCloseClick)
        } else {
            AgentInputBar(
                message = state.inputMessage,
                isLoading = state.isSending,
                onMessageChange = onMessageChange,
                onSendClick = onSendClick,
                onSessionsClick = onSessionsClick,
            )
        }
    }
}

@Composable
private fun AgentTopBar(
    title: String?,
    showTitle: Boolean,
    showHamburger: Boolean,
    showOverflowMenu: Boolean,
    hasConversation: Boolean,
    onMenuClick: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onGiveFeedbackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showHamburger) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_alignment_left),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }

        if (showTitle) {
            Text(
                text = title ?: stringResource(R.string.agent_title),
                style = Theme.brockmann.body.m.regular,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Box {
            IconButton(onClick = onOverflowClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_three_dots),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = onOverflowDismiss,
                modifier = Modifier.background(Color(0xFF1A2236)),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.agent_give_feedback),
                            color = Color.White,
                            style = Theme.brockmann.body.s.medium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = onGiveFeedbackClick,
                )
                if (hasConversation) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.agent_delete_chat_session),
                                color = Theme.v2.colors.alerts.error,
                                style = Theme.brockmann.body.s.medium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.trash_outline),
                                contentDescription = null,
                                tint = Theme.v2.colors.alerts.error,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = onDeleteClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentWelcomeState(
    isAuthenticating: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background glow beam (large, from top)
        Image(
            painter = painterResource(R.drawable.agent_glow_beam_3x),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(440.dp).offset(y = (-140).dp),
            contentScale = ContentScale.FillBounds,
            alpha = 0.7f,
        )

        // Orb glow (bright center)
        Image(
            painter = painterResource(R.drawable.agent_orb_glow_3x),
            contentDescription = null,
            modifier = Modifier.size(180.dp).align(Alignment.TopCenter).offset(y = 200.dp),
            contentScale = ContentScale.Fit,
        )

        // Orb itself
        Image(
            painter = painterResource(R.drawable.agent_orb_3x),
            contentDescription = null,
            modifier = Modifier.size(50.dp).align(Alignment.TopCenter).offset(y = 240.dp),
            contentScale = ContentScale.Fit,
        )

        // Text content
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(y = 310.dp)
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.agent_welcome_auth_title),
                style = Theme.brockmann.headings.title2,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 16.dp)

            Text(
                text = stringResource(R.string.agent_welcome_auth_subtitle),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )

            if (errorMessage != null) {
                UiSpacer(size = 16.dp)
                Text(
                    text = errorMessage,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.alerts.error,
                    textAlign = TextAlign.Center,
                )
            }

            if (isAuthenticating) {
                UiSpacer(size = 24.dp)
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = CtaPrimary,
                )
            }
        }
    }
}

private val TextPrimary = Color(0xFFF0F4FC)
private val ChipBlueBg = Color(0x194879FD)
private val ChipGreenBg = Color(0x0D13C89D)
private val ChipOrangeBg = Color(0x1AFFA500)
private val ChipBorder = Color(0x08FFFFFF)

private data class StarterChipStyle(val iconRes: Int, val bgColor: Color)

private val STARTER_CHIP_STYLES =
    listOf(
        StarterChipStyle(R.drawable.ic_agent_chip_plugins, ChipBlueBg),
        StarterChipStyle(R.drawable.ic_agent_chip_earn, ChipBlueBg),
        StarterChipStyle(R.drawable.ic_agent_chip_send, ChipGreenBg),
        StarterChipStyle(R.drawable.ic_agent_chip_swap, ChipOrangeBg),
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEmptyState(
    starters: List<String>,
    isLoadingStarters: Boolean,
    errorMessage: String?,
    onStarterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background glow beam (large, from top)
        Image(
            painter = painterResource(R.drawable.agent_glow_beam_3x),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(440.dp).offset(y = (-140).dp),
            contentScale = ContentScale.FillBounds,
            alpha = 0.7f,
        )

        // Orb glow
        Image(
            painter = painterResource(R.drawable.agent_orb_glow_3x),
            contentDescription = null,
            modifier = Modifier.size(180.dp).align(Alignment.TopCenter).offset(y = 120.dp),
            contentScale = ContentScale.Fit,
        )

        // Orb
        Image(
            painter = painterResource(R.drawable.agent_orb_3x),
            contentDescription = null,
            modifier = Modifier.size(50.dp).align(Alignment.TopCenter).offset(y = 162.dp),
            contentScale = ContentScale.Fit,
        )

        // Title + subtitle (single text block with blank line, matching Figma 18sp)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(y = 248.dp)
                    .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    stringResource(R.string.agent_welcome_title) +
                        "\n\n" +
                        stringResource(R.string.agent_welcome_subtitle),
                style = Theme.brockmann.body.l.regular,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            if (errorMessage != null) {
                UiSpacer(size = 16.dp)
                Text(
                    text = errorMessage,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.alerts.error,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Starter chips at the bottom
        Column(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
        ) {
            AnimatedVisibility(
                visible = starters.isNotEmpty() && !isLoadingStarters,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    starters.forEachIndexed { index, starter ->
                        val style =
                            STARTER_CHIP_STYLES.getOrElse(index) { STARTER_CHIP_STYLES.first() }
                        AgentStarterChip(
                            text = starter,
                            iconRes = style.iconRes,
                            bgColor = style.bgColor,
                            onClick = { onStarterClick(starter) },
                        )
                    }
                }
            }

            if (isLoadingStarters) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Theme.v2.colors.text.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentStarterChip(
    text: String,
    iconRes: Int,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                .background(bgColor)
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = Theme.brockmann.supplementary.caption,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AgentChatMessageItem(message: AgentChatMessage, modifier: Modifier = Modifier) {
    val toolCall = message.toolCall
    when {
        toolCall != null -> AgentToolCallRow(toolCall = toolCall, modifier = modifier)
        message.isError -> AgentErrorMessageItem(message = message, modifier = modifier)
        else -> {
            val isUser = message.role == AgentChatRole.User
            if (isUser) {
                Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier =
                            Modifier.widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1C2B3F))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.content,
                            style = Theme.brockmann.body.s.medium,
                            color = Color.White,
                        )
                    }
                }
            } else {
                Column(modifier = modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.drawable.agent_orb_3x),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                    UiSpacer(size = 8.dp)
                    Text(
                        text = message.content,
                        style = Theme.brockmann.body.s.medium,
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentErrorMessageItem(message: AgentChatMessage, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier =
                Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Theme.v2.colors.alerts.error.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.error,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Text(
                text = message.content,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.alerts.error,
            )
        }
    }
}

@Composable
private fun AgentToolCallRow(toolCall: AgentToolCallInfo, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (toolCall.status) {
            AgentToolCallStatus.RUNNING ->
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = Theme.v2.colors.text.secondary,
                )
            AgentToolCallStatus.SUCCESS ->
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color(0xFF33C4A0),
                    modifier = Modifier.size(14.dp),
                )
            AgentToolCallStatus.ERROR ->
                Icon(
                    painter = painterResource(R.drawable.close_2),
                    contentDescription = null,
                    tint = Theme.v2.colors.alerts.error,
                    modifier = Modifier.size(14.dp),
                )
        }
        Text(
            text = toolCall.title.uppercase(),
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
private fun AgentThinkingIndicator(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Image(
            painter = painterResource(R.drawable.agent_orb_3x),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit,
        )
        UiSpacer(size = 8.dp)
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val transition = rememberInfiniteTransition(label = "thinking")
            val alpha by
                transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "alpha",
                )
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Theme.v2.colors.text.secondary.copy(alpha = alpha),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.agent_analyzing),
                style = Theme.brockmann.supplementary.captionSmall,
                color = Theme.v2.colors.text.secondary.copy(alpha = alpha),
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
    onSessionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Wallet icon button (left)
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Surface1)
                    .border(1.dp, BorderLight, RoundedCornerShape(40.dp))
                    .clickable(onClick = onSessionsClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.wallet),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        // Text field with send/mic button inside (center+right)
        Box(
            modifier =
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Surface1)
                    .border(1.dp, BorderLight, RoundedCornerShape(40.dp))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    textStyle = Theme.brockmann.body.s.medium.copy(color = Color.White),
                    colors =
                        TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            cursorColor = CtaPrimary,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendClick() }),
                    singleLine = true,
                )
                val hasText = message.isNotBlank()
                IconButton(
                    onClick = { if (hasText) onSendClick() },
                    enabled = hasText && !isLoading,
                    modifier =
                        Modifier.padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CtaPrimary)
                            .border(1.dp, CtaBorder, CircleShape),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            painter =
                                painterResource(
                                    if (hasText) R.drawable.send else R.drawable.ic_microphone
                                ),
                            contentDescription =
                                if (hasText) stringResource(R.string.agent_send) else null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentPasswordInputBar(
    onPasswordSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // X button (left) — Figma: 52dp circle, Surface1 bg, BorderLight border
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, BorderLight, CircleShape)
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cross_large),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        // Password field + unlock button
        Box(
            modifier =
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface1)
                    .border(1.dp, BorderLight, RoundedCornerShape(24.dp))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.agent_enter_vault_password),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.tertiary,
                        )
                    },
                    textStyle = Theme.brockmann.body.s.medium.copy(color = Color.White),
                    visualTransformation = PasswordVisualTransformation(),
                    colors =
                        TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            cursorColor = CtaPrimary,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                        ),
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = { if (password.isNotBlank()) onPasswordSubmit(password) }
                        ),
                    singleLine = true,
                )
                IconButton(
                    onClick = { if (password.isNotBlank()) onPasswordSubmit(password) },
                    modifier =
                        Modifier.padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CtaPrimary)
                            .border(1.dp, CtaBorder, CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_unlocked),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentNonFastVaultBar(onCloseClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, BorderLight, CircleShape)
                    .clickable(onClick = onCloseClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cross_large),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Box(
            modifier =
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = InfoLight,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.agent_requires_fast_vault),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }
    }
}
