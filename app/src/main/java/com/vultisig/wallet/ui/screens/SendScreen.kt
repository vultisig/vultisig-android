package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.FormEntry
import com.vultisig.wallet.ui.components.FormTextFieldCard
import com.vultisig.wallet.ui.components.FormTokenCard
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TokenCard
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiLinearProgressIndicator
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.SendViewModel
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SendScreen(
    navController: NavController,
    qrCodeResult: String?,
    viewModel: SendViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = errorText.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = viewModel::dismissError,
        )
    }

    LaunchedEffect(qrCodeResult) {
        viewModel.setAddressFromQrCode(qrCodeResult)
    }

    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.send_screen_title),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(all = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                UiLinearProgressIndicator(
                    progress = 0.25f,
                )

                // size 0 but still adds margin because of verticalArrangement
                UiSpacer(size = 0.dp)

                val selectedToken = state.selectedCoin

                FormTokenCard(
                    selectedTitle = selectedToken?.title ?: "",
                    availableToken = selectedToken?.balance ?: "",
                    selectedIcon = selectedToken?.logo
                        ?: R.drawable.ethereum,
                    isExpanded = state.isTokensExpanded,
                    onClick = viewModel::toggleTokens,
                ) {
                    state.availableTokens.forEach { token ->
                        UiHorizontalDivider(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                        )

                        TokenCard(
                            title = token.title,
                            icon = token.logo,
                            actionIcon = if (token == selectedToken)
                                R.drawable.check
                            else null,
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
                        style = Theme.menlo.body1,
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
                    keyboardType = KeyboardType.Text,
                    textFieldState = viewModel.addressFieldState,
                ) {
                    val clipboard = LocalClipboardManager.current

                    UiIcon(
                        drawableResId = R.drawable.copy,
                        size = 20.dp,
                        onClick = {
                            clipboard.getText()
                                ?.toString()
                                ?.let(viewModel::setOutputAddress)
                        }
                    )

                    UiSpacer(size = 8.dp)
                    UiIcon(
                        drawableResId = R.drawable.camera,
                        size = 20.dp,
                        onClick = viewModel::scanAddress,
                    )
                }

                FormTextFieldCard(
                    title = stringResource(R.string.send_amount),
                    hint = stringResource(R.string.send_amount_hint),
                    keyboardType = KeyboardType.Number,
                    textFieldState = viewModel.tokenAmountFieldState,
                ) {
                    Text(
                        text = stringResource(R.string.send_screen_max),
                        color = Theme.colors.neutral100,
                        style = Theme.menlo.body1,
                        modifier = Modifier
                            .clickable(onClick = viewModel::chooseMaxTokenAmount),
                    )
                }

                FormTextFieldCard(
                    title = stringResource(R.string.send_amount_currency, state.fiatCurrency),
                    hint = stringResource(R.string.send_amount_currency_hint),
                    keyboardType = KeyboardType.Number,
                    textFieldState = viewModel.fiatAmountFieldState,
                )
                if (state.showGasFee) {
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
                onClick = viewModel::send,
            )
        }
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    SendScreen(
        navController = rememberNavController(),
        qrCodeResult = null,
    )
}