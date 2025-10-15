package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ColumnScope.DefiExpandedTopbarContent(
    state: VaultAccountsUiModel,
    onToggleBalanceVisibility: () -> Unit,
) {

    UiSpacer(
        weight = 1f
    )

    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(
                    size = 16.dp
                )
            )
            .clip(
                shape = RoundedCornerShape(
                    size = 16.dp
                )
            )
    ) {


        CoinsAround()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 16.dp
                )
                .align(
                    Alignment.Center
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_defi_portfolio),
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.l.medium
            )

            UiSpacer(
                size = 16.dp
            )
            BalanceBanner(
                isVisible = state.isBalanceValueVisible,
                balance = state.totalFiatValue,
                onToggleVisibility = onToggleBalanceVisibility
            )
        }

    }
    UiSpacer(
        weight = 1f
    )
}