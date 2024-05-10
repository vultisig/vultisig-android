package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.base_components.MultiColorButton
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.ui.components.FormEntry
import com.vultisig.wallet.ui.components.FormTextFieldCard
import com.vultisig.wallet.ui.components.FormTokenCard
import com.vultisig.wallet.ui.components.TokenCard
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiLinearProgressIndicator
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.SendViewModel
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SendScreen(
    navController: NavController,
    viewModel: SendViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = stringResource(R.string.send_screen_title),
            startIcon = R.drawable.caret_left,
            navController = navController
        )
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(all = 16.dp),
            ) {
                UiLinearProgressIndicator(
                    progress = 0.25f,
                )

                // size 0 but still adds margin because of verticalArrangement
                UiSpacer(size = 0.dp)

                FormTokenCard(
                    selectedTitle = state.selectedCoin?.title ?: "",
                    availableToken = state.selectedCoin?.balance ?: "",
                    selectedIcon = state.selectedCoin?.logo
                        ?: R.drawable.ethereum,
                    isExpanded = state.isTokensExpanded,
                    onClick = viewModel::toggleTokens,
                ) {
                    state.availableTokens.forEach { token ->
                        HorizontalDivider(
                            color = Theme.colors.oxfordBlue200,
                            modifier = Modifier
                                .padding(horizontal = 12.dp),
                        )

                        TokenCard(
                            title = token.title,
                            icon = token.logo,
                            onClick = { viewModel.selectToken(token) }
                        )
                    }
                }

                FormEntry(
                    title = stringResource(R.string.send_from_address),
                ) {
                    Text(
                        text = state.from,
                        color = Theme.colors.neutral100,
                        style = Theme.montserrat.body1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(
                                horizontal = 12.dp,
                                vertical = 16.dp
                            ),
                    )
                }

                FormTextFieldCard(
                    title = stringResource(R.string.send_to_address),
                    hint = stringResource(R.string.send_to_address_hint),
                    textFieldState = rememberTextFieldState(),
                ) {
                    UiIcon(
                        drawableResId = R.drawable.copy,
                        size = 20.dp,
                    )
                    UiSpacer(size = 8.dp)
                    UiIcon(
                        drawableResId = R.drawable.camera,
                        size = 20.dp,
                    )
                }

                FormTextFieldCard(
                    title = stringResource(R.string.send_amount),
                    hint = stringResource(R.string.send_amount_hint),
                    textFieldState = rememberTextFieldState(),
                ) {
                    Text(
                        text = "MAX",
                        color = Theme.colors.neutral100,
                        style = Theme.menlo.body1,
                    )
                }

                FormTextFieldCard(
                    title = stringResource(R.string.send_amount_currency), // TODO add any currency
                    hint = stringResource(R.string.send_amount_currency_hint),
                    textFieldState = rememberTextFieldState(),
                )

                Row {
                    Text(
                        text = stringResource(R.string.send_gas_title),
                        color = Theme.colors.neutral100,
                        style = Theme.montserrat.body1,
                    )
                    UiSpacer(weight = 1f)
                    Text(
                        text = state.fee ?: "",
                        color = Theme.colors.neutral100,
                        style = Theme.menlo.body1
                    )
                }

                UiSpacer(size = 80.dp)
            }

            MultiColorButton(
                text = stringResource(R.string.send_continue_button),
                textColor = Theme.colors.oxfordBlue800,
                minHeight = 44.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(all = 16.dp),
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    SendScreen(
        navController = rememberNavController()
    )
}