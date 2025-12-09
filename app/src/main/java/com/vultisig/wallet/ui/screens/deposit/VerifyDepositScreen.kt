package com.vultisig.wallet.ui.screens.deposit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsHoldableButton
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositViewModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.SwapToken
import com.vultisig.wallet.ui.screens.swap.VerifyCardDetails
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import java.math.BigInteger

@Composable
internal fun VerifyDepositScreen(
    navController: NavController? = null,
    viewModel: VerifyDepositViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit = remember(context) {
        {
            context.launchBiometricPrompt(
                promptTitle = promptTitle,
                onAuthorizationSuccess = viewModel::authFastSign,
            )
        }
    }

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(id = R.string.dialog_default_error_title),
            text = errorText.asString(),
            onDismiss = viewModel::dismissError,
        )
    }

    VerifyDepositScreen(
        hasToolbar = navController != null, // comes from new graph, and not legacy
        state = state,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConfirm = viewModel::confirm,
        onFastSignClick = {
            if (!viewModel.tryToFastSignWithPassword()) {
                authorize()
            }
        },
        onBackClick = { navController?.popBackStack() },
    )
}

@Composable
internal fun VerifyDepositScreen(
    state: VerifyDepositUiModel,
    hasToolbar: Boolean = false,
    confirmTitle: String,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onBackClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            if (hasToolbar) {
                VsTopAppBar(
                    title = stringResource(R.string.verify_deposit_function_overview),
                    onBackClick = onBackClick,
                )
            }
        },
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .fillMaxSize(),
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(all = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {

                val tx = state.depositTransactionUiModel


                Column(
                    modifier = Modifier
                        .background(
                            color = Theme.v2.colors.backgrounds.secondary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(
                            all = 24.dp,
                        )
                ) {
                    Text(
                        text = stringResource(R.string.verify_deposit_sending),
                        style = Theme.brockmann.headings.subtitle,
                        color = Theme.v2.colors.text.light,
                    )

                    UiSpacer(24.dp)

                    SwapToken(
                        valuedToken = tx.token,
                        isLoading = state.isLoading,
                    )

                    UiSpacer(12.dp)

                    VerifyCardDivider(8.dp)

                    tx.srcAddress.takeIf { it.isNotEmpty() }?.let {
                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_from_title),
                            subtitle = tx.srcAddress
                        )

                        VerifyCardDivider(0.dp)
                    }


                    if (tx.dstAddress.isNotEmpty()) {
                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_to_title),
                            subtitle = tx.dstAddress
                        )
                    }
                    if (tx.thorAddress.isNotEmpty()) {
                        VerifyCardDetails(
                            title = stringResource(R.string.thor_address),
                            subtitle = tx.thorAddress
                        )
                        VerifyCardDivider(0.dp)
                    }
                    if (tx.operation.isNotEmpty()) {
                        VerifyCardDetails(
                            title = stringResource(R.string.operation),
                            subtitle = tx.operation
                        )
                        VerifyCardDivider(0.dp)
                    }
                    

                    if (tx.memo.isNotEmpty()) {
                        if (tx.dstAddress.isNotEmpty())
                            VerifyCardDivider(0.dp)

                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_memo_title),
                            subtitle = tx.memo,
                            showAllContent = true
                        )
                    }

                    if (tx.token.value.isNotEmpty() && try {
                            tx.token.value.toBigInteger() > BigInteger.ZERO
                        } catch (e: Exception) {
                            false
                        }
                    ) {
                        VerifyCardDivider(0.dp)
                        VerifyCardDetails(
                            title = stringResource(R.string.verify_transaction_amount_title),
                            subtitle = (tx.token.value)
                        )
                    }

                    val hasContent =
                        tx.srcAddress.isNotEmpty()
                                || tx.dstAddress.isNotEmpty()
                                || tx.memo.isNotEmpty()

                    if (hasContent) {
                        VerifyCardDivider(0.dp)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 12.dp,
                            )
                    ) {
                        Text(
                            text = stringResource(R.string.verify_deposit_network),
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.text.extraLight,
                            maxLines = 1,
                        )

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val chain = state.depositTransactionUiModel.token.token.chain

                            if (state.isLoading) {
                                UiPlaceholderLoader(
                                    modifier = Modifier
                                        .height(20.dp)
                                        .width(150.dp)
                                )
                            } else {
                                Image(
                                    painter = painterResource(chain.logo),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp),
                                )

                                Text(
                                    text = chain.raw,
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.v2.colors.text.primary,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                            }
                        }
                    }

                    VerifyCardDivider(0.dp)

                    UiSpacer(12.dp)

                    EstimatedNetworkFee(
                        tokenGas = tx.networkFeeTokenValue,
                        fiatGas = tx.networkFeeFiatValue,
                        isLoading = state.isLoading,
                    )
                }
            }
        },
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp
                    )
            ) {

                if (state.hasFastSign) {
                    Text(
                        text = stringResource(R.string.verify_deposit_hold_paired),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.extraLight,
                        textAlign = TextAlign.Center,
                    )
                    VsHoldableButton(
                        label = stringResource(R.string.verify_deposit_sign_transaction),
                        onLongClick = onConfirm,
                        onClick = onFastSignClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    VsButton(
                        state = state.isLoading.takeIf { it }?.let { VsButtonState.Disabled }
                            ?: VsButtonState.Enabled,
                        label = confirmTitle,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    )
}


@Preview
@Composable
private fun VerifyDepositScreenPreview() {
    VerifyDepositScreen(
        state = VerifyDepositUiModel(
            depositTransactionUiModel = DepositTransactionUiModel(
                token = ValuedToken(
                    token = Coins.ThorChain.RUNE,
                    value = "1 RUNE",
                    fiatValue = "$1.37"
                ),
                networkFeeFiatValue = "$0.03",
                networkFeeTokenValue = "0.02 RUNE",
                srcAddress = "123abc456bca",
                dstAddress = "123abc456bca",
                thorAddress = "123abc456bca",
                operation = "mint",
                memo = "BOND:addressHere"
            ),
        ),
        confirmTitle = "title",
        onConfirm = {},
        onFastSignClick = {}
    )
}