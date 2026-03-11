package com.vultisig.wallet.ui.screens.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.models.agent.AgentConversationsUiModel
import com.vultisig.wallet.ui.models.agent.AgentConversationsViewModel
import com.vultisig.wallet.ui.models.agent.ConversationItemUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AgentConversationsScreen(viewModel: AgentConversationsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    AgentConversationsContent(
        state = state,
        onConversationClick = viewModel::onConversationClick,
        onNewChatClick = viewModel::onNewChatClick,
        onBackClick = viewModel::onBackClick,
    )
}

@Composable
private fun AgentConversationsContent(
    state: AgentConversationsUiModel,
    onConversationClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_caret_left),
                    contentDescription = null,
                    tint = Theme.v2.colors.text.primary,
                )
            }

            Text(
                text = stringResource(R.string.agent_session_history),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.buttons.ctaPrimary)
                        .clickable(onClick = onNewChatClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.plus),
                    contentDescription = stringResource(R.string.agent_new_chat),
                    tint = Theme.v2.colors.text.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Theme.v2.colors.buttons.ctaPrimary)
            }
        } else if (state.conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.agent_no_conversations),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = conversation.title,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(16.dp))

        UiHorizontalDivider()
    }
}
