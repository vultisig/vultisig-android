package com.vultisig.wallet.ui.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySwapScreen(
    viewModel: VerifySwapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit = remember(context) {
        {
            context.launchBiometricPrompt(
                promptTitle = promptTitle,
                onAuthorizationSuccess =  viewModel::authFastSign,
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

    VerifySwapScreen(
        state = state,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConsentReceiveAmount = viewModel::consentReceiveAmount,
        onConsentAmount = viewModel::consentAmount,
        onConfirm = viewModel::confirm,
        onConsentAllowance = viewModel::consentAllowance,
        onFastSignClick = {
            if (!viewModel.tryToFastSignWithPassword()) {
                authorize()
            }
        },
    )
}

@Composable
internal fun VerifySwapScreen(
    state: VerifySwapUiModel,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    onConsentReceiveAmount: (Boolean) -> Unit = {},
    onConsentAmount: (Boolean) -> Unit = {},
    onConsentAllowance: (Boolean) -> Unit = {},
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    VerifySwapScreen(
        provider = state.provider.asString(),
        swapTransactionUiModel = state.swapTransactionUiModel,
        hasAllConsents = state.hasAllConsents,
        consentAmount = state.consentAmount,
        consentReceiveAmount = state.consentReceiveAmount,
        consentAllowance = state.consentAllowance,
        confirmTitle = confirmTitle,
        isConsentsEnabled = isConsentsEnabled,
        hasFastSign = state.hasFastSign,
        onConsentReceiveAmount = onConsentReceiveAmount,
        onConsentAmount = onConsentAmount,
        onConsentAllowance = onConsentAllowance,
        onFastSignClick = onFastSignClick,
        onConfirm = onConfirm,
    )
}

@Composable
private fun VerifySwapScreen(
    provider: String,
    swapTransactionUiModel: SwapTransactionUiModel,
    hasAllConsents: Boolean,
    consentAmount: Boolean,
    consentReceiveAmount: Boolean,
    consentAllowance: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    hasFastSign: Boolean,
    onConsentReceiveAmount: (Boolean) -> Unit,
    onConsentAmount: (Boolean) -> Unit,
    onConsentAllowance: (Boolean) -> Unit,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(all = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {

                Column(
                    modifier = Modifier
                        .background(
                            color = Theme.colors.backgrounds.secondary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(
                            all = 24.dp,
                        )
                ) {
                    Text(
                        text = stringResource(R.string.verify_swap_youre_swapping_title),
                        style = Theme.brockmann.headings.subtitle,
                        color = Theme.colors.text.light,
                    )

                    UiSpacer(24.dp)

                    SwapToken(
                        token = swapTransactionUiModel.srcToken,
                        value = swapTransactionUiModel.srcTokenValue,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            VerticalDivider(
                                thickness = 1.dp,
                                color = Theme.colors.borders.light,
                                modifier = Modifier
                                    .height(16.dp)
                            )

                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_down),
                                contentDescription = null,
                                tint = Theme.colors.primary.accent4,
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = Theme.colors.backgrounds.tertiary,
                                        shape = CircleShape,
                                    )
                                    .padding(6.dp)
                            )

                            VerticalDivider(
                                thickness = 1.dp,
                                color = Theme.colors.borders.light,
                                modifier = Modifier
                                    .height(16.dp)
                            )
                        }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Theme.colors.borders.light,
                        )
                    }


                    SwapToken(
                        token = swapTransactionUiModel.dstToken,
                        value = swapTransactionUiModel.dstTokenValue,
                    )

                    VerifyCardDivider(
                        size = 20.dp,
                    )

                    VerifyCardDetails(
                        title = stringResource(R.string.verify_swap_screen_total_fees),
                        subtitle = swapTransactionUiModel.totalFee,
                    )
                }

                if (isConsentsEnabled) {
                    Column {
                        VsCheckField(
                            title = stringResource(R.string.verify_swap_consent_amount),
                            isChecked = consentAmount,
                            onCheckedChange = onConsentAmount,
                        )

                        VsCheckField(
                            title = stringResource(R.string.verify_swap_agree_receive_amount),
                            isChecked = consentReceiveAmount,
                            onCheckedChange = onConsentReceiveAmount,
                        )

                        if (swapTransactionUiModel.hasConsentAllowance) {
                            VsCheckField(
                                title = stringResource(R.string.verify_swap_agree_allowance),
                                isChecked = consentAllowance,
                                onCheckedChange = onConsentAllowance,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    )
            ) {
                if (hasFastSign) {
                    VsButton(
                        label = stringResource(R.string.verify_transaction_fast_sign_btn_title),
                        modifier = Modifier
                            .fillMaxWidth(),
                        state = if (isConsentsEnabled && !hasAllConsents)
                            VsButtonState.Disabled else VsButtonState.Enabled,
                        onClick = onFastSignClick,
                    )
                }

                VsButton(
                    label = confirmTitle,
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = if (isConsentsEnabled && !hasAllConsents)
                        VsButtonState.Disabled else VsButtonState.Enabled,
                    onClick = onConfirm,
                )
            }
        }
    )
}

@Composable
private fun SwapToken(
    token: Coin,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenLogo(
            logo = Coins.getCoinLogo(token.logo),
            title = token.ticker,
            errorLogoModifier = Modifier
                .size(24.dp),
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
                    shape = CircleShape,
                ),
        )

        Text(
            text = value,
            style = Theme.brockmann.headings.title3,
            color = Theme.colors.text.primary,
        )
    }
}

@Composable
internal fun VerifyCardDivider(
    size: Dp,
) {
    HorizontalDivider(
        thickness = 1.dp,
        color = Theme.colors.borders.light,
        modifier = Modifier
            .padding(
                vertical = size,
            )
    )
}

@Composable
private fun VerifyCardDetails(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp,
            )
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
        )

        Text(
            text = subtitle,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
        )
    }
}

@Preview
@Composable
private fun VerifySwapScreenPreview() {
    VerifySwapScreen(
        provider = "THORChain",
        hasAllConsents = false,
        consentAmount = true,
        consentReceiveAmount = false,
        swapTransactionUiModel = SwapTransactionUiModel(
            srcTokenValue = "1 RUNE",
            dstTokenValue = "1 ETH",
            totalFee = "1.00$",
            hasConsentAllowance = true,
        ),
        consentAllowance = true,
        confirmTitle = "Sign",
        hasFastSign = false,
        onConsentReceiveAmount = {},
        onConsentAmount = {},
        onConsentAllowance = {},
        onFastSignClick = {},
        onConfirm = {},
    )
}