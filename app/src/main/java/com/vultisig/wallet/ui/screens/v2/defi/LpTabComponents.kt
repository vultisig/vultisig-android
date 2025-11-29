package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.defi.LpPositionUiModel
import com.vultisig.wallet.ui.models.defi.LpTabUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun LpTabContent(
    state: LpTabUiModel,
    onClickAdd: () -> Unit,
    onClickRemove: () -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.positions.forEach { lpPosition ->
            LpWidget(
                state = lpPosition,
                isLoading = state.isLoading,
                onClickAdd = onClickAdd,
                onClickRemove = onClickRemove,
            )
        }
    }
}

@Composable
internal fun LpWidget(
    state: LpPositionUiModel,
    isLoading: Boolean = false,
    onClickAdd: () -> Unit,
    onClickRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.v2.colors.backgrounds.secondary)
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.normal,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = state.icon),
                contentDescription = null,
                modifier = Modifier.size(46.dp)
            )

            UiSpacer(12.dp)

            Column {
                Text(
                    text = state.titleLp,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.extraLight,
                )

                UiSpacer(4.dp)

                if (isLoading) {
                    UiPlaceholderLoader(
                        modifier = Modifier
                            .width(120.dp)
                            .height(28.dp)
                    )
                } else {
                    Text(
                        text = state.totalPriceLp,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                    )
                }
            }
        }

        UiSpacer(16.dp)

        UiHorizontalDivider()

        UiSpacer(16.dp)

        if (state.apr != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoItem(
                    icon = R.drawable.ic_icon_percentage,
                    label = stringResource(R.string.apy),
                    value = null,
                )

                UiSpacer(1f)

                Text(
                    text = state.apr,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            }

            UiSpacer(16.dp)
        }

        Column {
            Text(
                text = stringResource(R.string.lp_position),
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )

            UiSpacer(6.dp)

            if (isLoading) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .width(180.dp)
                        .height(24.dp)
                        .padding(top = 4.dp)
                )
            } else {
                Text(
                    text = state.position,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.headings.title3,
                )
            }
            UiSpacer(16.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = stringResource(R.string.remove),
                icon = R.drawable.ic_circle_minus,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickRemove,
                modifier = Modifier.weight(1f),
                enabled = true,
                iconCircleColor = Theme.v2.colors.text.extraLight
            )

            ActionButton(
                title = stringResource(R.string.add),
                icon = R.drawable.ic_circle_plus,
                background = Theme.v2.colors.primary.accent3,
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickAdd,
                modifier = Modifier.weight(1f),
                enabled = true,
                iconCircleColor = Theme.v2.colors.primary.accent4
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LpWidgetPreview() {

    LpWidget(
        state = LpPositionUiModel(
            titleLp = "ETH/USDC",
            totalPriceLp = "$4,300",
            icon = R.drawable.ethereum,
            apr = "12.5%",
            position = "0.5 ETH / 1500 USDC"
        ),
        isLoading = false,
        onClickAdd = {},
        onClickRemove = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun LpWidgetLoadingPreview() {
    LpWidget(
        state = LpPositionUiModel(
            titleLp = "BTC/USDT",
            totalPriceLp = "$0",
            icon = R.drawable.ethereum,
            apr = null,
            position = "Loading..."
        ),
        isLoading = true,
        onClickAdd = {},
        onClickRemove = {}
    )
}