package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.reorderable.VerticalDoubleReorderList
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.SortAccountItemUiModel
import com.vultisig.wallet.ui.models.SortAccountsUiModel
import com.vultisig.wallet.ui.models.SortAccountsViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SortAccountsScreen(viewModel: SortAccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SortAccountsScreen(
        state = state,
        onBackClick = viewModel::back,
        onSaveClick = viewModel::save,
        onMovePinned = viewModel::movePinned,
        onMoveUnpinned = viewModel::moveUnpinned,
        onTogglePin = viewModel::togglePin,
    )
}

@Composable
internal fun SortAccountsScreen(
    state: SortAccountsUiModel,
    onBackClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onMovePinned: (from: Int, to: Int) -> Unit = { _, _ -> },
    onMoveUnpinned: (from: Int, to: Int) -> Unit = { _, _ -> },
    onTogglePin: (SortAccountItemUiModel) -> Unit = {},
) {
    V2Scaffold(
        title = stringResource(R.string.sort_accounts_title),
        onBackClick = onBackClick,
        actions = {
            Text(
                text = stringResource(R.string.address_book_edit_mode_done),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.primary.accent4,
                modifier = Modifier.clickOnce(onClick = onSaveClick).padding(all = 12.dp),
            )
        },
    ) {
        VerticalDoubleReorderList(
            dataT = state.pinnedAccounts,
            dataR = state.unpinnedAccounts,
            keyT = { it.chainId },
            keyR = { it.chainId },
            onMoveT = onMovePinned,
            onMoveR = onMoveUnpinned,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            beforeContents =
                if (state.pinnedAccounts.isNotEmpty()) {
                    listOf { SectionHeader(title = stringResource(R.string.sort_accounts_pinned)) }
                } else null,
            midContents =
                if (state.pinnedAccounts.isNotEmpty() && state.unpinnedAccounts.isNotEmpty()) {
                    listOf {
                        SectionHeader(title = stringResource(R.string.sort_accounts_unpinned))
                    }
                } else null,
            contentT = { item ->
                SortAccountItem(item = item, onTogglePin = { onTogglePin(item) })
            },
            contentR = { item -> SortAccountItem(item = item, onTogglePin = { onTogglePin(item) }) },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.tertiary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
private fun SortAccountItem(item: SortAccountItemUiModel, onTogglePin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_drag_handle,
            size = 20.dp,
            tint = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(size = 12.dp)

        Image(
            painter = painterResource(id = item.logo),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )

        UiSpacer(size = 12.dp)

        Text(
            text = item.chainName,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.weight(1f),
        )

        UiIcon(
            drawableResId = R.drawable.ic_pin,
            size = 20.dp,
            tint =
                if (item.isPinned) Theme.v2.colors.primary.accent4
                else Theme.v2.colors.text.tertiary,
            contentDescription =
                if (item.isPinned) "Unpin ${item.chainName}" else "Pin ${item.chainName}",
            modifier = Modifier.clickOnce(onClick = onTogglePin),
        )
    }
}
