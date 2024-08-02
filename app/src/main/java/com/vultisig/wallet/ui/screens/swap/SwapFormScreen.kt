package com.vultisig.wallet.ui.screens.swap

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.BasicFormTextField
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.components.library.form.FormError
import com.vultisig.wallet.ui.components.library.form.FormTokenSelection
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormViewModel
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigInteger


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwapFormScreen(
    vaultId: String,
    chainId: String?,
    viewModel: SwapFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(vaultId, chainId) {
        viewModel.loadData(vaultId, chainId)
    }

    SwapFormScreen(
        state = state,
        srcAmountTextFieldState = viewModel.srcAmountState,
        onAmountLostFocus = viewModel::validateAmount,
        onSwap = viewModel::swap,
        onSelectSrcToken = viewModel::selectSrcToken,
        onDismissError = viewModel::hideError,
        onSelectDstToken = viewModel::selectDstToken,
        onFlipSelectedTokens = viewModel::flipSelectedTokens,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwapFormScreen(
    state: SwapFormUiModel,
    srcAmountTextFieldState: TextFieldState,
    onAmountLostFocus: () -> Unit = {},
    onSelectSrcToken: () -> Unit = {},
    onSelectDstToken: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onFlipSelectedTokens: () -> Unit = {},
    onSwap: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            MultiColorButton(
                text = stringResource(R.string.send_continue_button),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                onClick = {
                    focusManager.clearFocus(true)
                    onSwap()
                },
                disabled = state.isSwapDisabled,
            )
        }
    ) {

        val errorText = state.error
        if (errorText != null) {
            UiAlertDialog(
                title = stringResource(R.string.dialog_default_error_title),
                text = errorText.asString(),
                confirmTitle = stringResource(R.string.try_again),
                onDismiss = onDismissError,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(it)
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp
                ),
        ) {
            FormTokenSelection(
                selectedToken = state.selectedSrcToken,
                onSelectToken = onSelectSrcToken,
            )

            Box {
                Column {
                    SwapFormTextField(
                        title = stringResource(id = R.string.swap_form_from_title),
                        hint = stringResource(id = R.string.swap_form_src_amount_hint),
                        fiatAmount = state.srcFiatValue,
                        textFieldState = srcAmountTextFieldState,
                        onLostFocus = onAmountLostFocus,
                    )

                    UiSpacer(size = 8.dp)

                    SwapFormTextContent(
                        title = stringResource(id = R.string.swap_form_dst_token_title),
                        fiatAmount = state.estimatedDstFiatValue,
                    ) {
                        Text(
                            text = state.estimatedDstTokenValue,
                            color = Theme.colors.neutral500,
                            style = Theme.menlo.heading5,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                UiIcon(
                    drawableResId = R.drawable.ic_swap_arrows,
                    size = 24.dp,
                    modifier = Modifier
                        .background(
                            color = Theme.colors.persianBlue400,
                            shape = CircleShape,
                        )
                        .padding(all = 8.dp)
                        .align(Alignment.Center),
                    onClick = onFlipSelectedTokens,
                )
            }

            FormTokenSelection(
                selectedToken = state.selectedDstToken,
                onSelectToken = onSelectDstToken,
            )

            UiSpacer(size = 0.dp)

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FormDetails(
                    title = stringResource(R.string.swap_screen_provider_title),
                    value = state.provider.asString(),
                )

                FormDetails(
                    title = stringResource(R.string.swap_form_gas_title),
                    value = state.gas,
                )

                FormDetails(
                    title = stringResource(R.string.swap_form_estimated_fees_title),
                    value = state.fee
                )
            }
            when {
                state.formError != null -> {
                    FormError(
                        errorMessage = state.formError.asString()
                    )
                }
                state.minimumAmount != BigInteger.ZERO.toString() -> {
                    FormError(
                        errorMessage = stringResource(
                            R.string.swap_form_minimum_amount,
                            state.minimumAmount,
                            state.selectedSrcToken?.title ?: ""
                        )
                    )
                }
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwapFormTextField(
    title: String,
    hint: String,
    fiatAmount: String,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit,
) {
    SwapFormTextContent(
        title = title,
        fiatAmount = fiatAmount
    ) {
        BasicFormTextField(
            hint = hint,
            keyboardType = KeyboardType.Number,
            textFieldState = textFieldState,
            textStyle = Theme.menlo.heading5,
            onLostFocus = onLostFocus,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun SwapFormTextContent(
    title: String,
    fiatAmount: String,
    content: @Composable () -> Unit,
) {
    FormCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 16.dp
            ),
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Theme.colors.neutral200,
                    style = Theme.menlo.body1,
                    modifier = Modifier
                )

                content()
            }

            Text(
                text = fiatAmount,
                color = Theme.colors.neutral500,
                style = Theme.menlo.body2,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
internal fun SwapFormScreenPreview() {
    UiBarContainer(
        navController = rememberNavController(),
        title = "Swap",
    ) {
        SwapFormScreen(
            state = SwapFormUiModel(
                estimatedDstTokenValue = "0",
            ),
            srcAmountTextFieldState = TextFieldState(),
        )
    }
}