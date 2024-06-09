package com.vultisig.wallet.ui.screens.swap

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.components.library.form.FormTextField
import com.vultisig.wallet.ui.components.library.form.FormTitleContainer
import com.vultisig.wallet.ui.components.library.form.FormTokenSelection
import com.vultisig.wallet.ui.components.library.form.TextFieldValidator
import com.vultisig.wallet.ui.components.library.form.UiTextFieldValidator
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormViewModel
import com.vultisig.wallet.ui.theme.Theme


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
        onSwap = viewModel::swap,
        onSelectSrcToken = viewModel::selectSrcToken,
        onSelectDstToken = viewModel::selectDstToken,
        onFlipSelectedTokens = viewModel::flipSelectedTokens,
        srcAmountValidator = viewModel::srcAmountValidator
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwapFormScreen(
    state: SwapFormUiModel,
    srcAmountTextFieldState: TextFieldState,
    srcAmountValidator: UiTextFieldValidator,
    onSelectSrcToken: (TokenBalanceUiModel) -> Unit = {},
    onSelectDstToken: (TokenBalanceUiModel) -> Unit = {},
    onFlipSelectedTokens: () -> Unit = {},
    onSwap: () -> Unit = {},
) {
    var focusState by remember {
        mutableStateOf<FocusState?>(null)
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp
                ),
        ) {
            FormTitleContainer(
                title = stringResource(R.string.swap_form_from_title),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FormTokenSelection(
                        selectedToken = state.selectedSrcToken,
                        availableTokens = state.availableTokens,
                        onSelectToken = onSelectSrcToken,
                    )

                    TextFieldValidator(
                        state = srcAmountTextFieldState,
                        validator = srcAmountValidator,
                        focusState = focusState
                    )
                    {
                        FormCard {
                            FormTextField(
                                hint = stringResource(R.string.swap_form_src_amount_hint),
                                keyboardType = KeyboardType.Number,
                                textFieldState = srcAmountTextFieldState,
                                onFocusStateChanged = {
                                    focusState = it
                                }
                            )
                        }
                    }
                }
            }

            UiIcon(
                drawableResId = R.drawable.ic_swap_arrows,
                size = 24.dp,
                modifier = Modifier
                    .padding(all = 8.dp)
                    .background(
                        color = Theme.colors.persianBlue400,
                        shape = CircleShape,
                    )
                    .padding(all = 8.dp),
                onClick = onFlipSelectedTokens,
            )

            FormTitleContainer(
                title = stringResource(R.string.swap_form_dst_token_title),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {


                    FormTokenSelection(
                        selectedToken = state.selectedDstToken,
                        availableTokens = state.availableTokens,
                        onSelectToken = onSelectDstToken,
                    )

                    FormCard {
                        Text(
                            text = state.estimatedDstTokenValue,
                            color = Theme.colors.neutral300,
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
                }
            }

            FormDetails(
                title = stringResource(R.string.swap_form_gas_title),
                value = state.gas,
            )

            FormDetails(
                title = stringResource(R.string.swap_form_estimated_fees_title),
                value = state.fee
            )

            FormDetails(
                title = stringResource(R.string.swap_form_estimated_time_title),
                value = state.estimatedTime.asString()
            )
        }

        MultiColorButton(
            text = stringResource(R.string.send_continue_button),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp,
                ),
            onClick = onSwap,
        )
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
            state = SwapFormUiModel(),
            srcAmountValidator = { null },
            srcAmountTextFieldState = TextFieldState(),
        )
    }
}