package com.vultisig.wallet.ui.screens.referral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MoreInfoBox
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.CreateReferralUiState
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel
import com.vultisig.wallet.ui.models.referral.FeesReferral
import com.vultisig.wallet.ui.models.referral.SearchStatusType
import com.vultisig.wallet.ui.models.referral.isError
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralCreateScreen(
    navController: NavController,
    model: CreateReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ReferralCreateScreen(
        state = state,
        searchTextFieldState = model.searchReferralTexFieldState,
        onBackPressed = navController::popBackStack,
        onSearchClick = model::onSearchReferralCode,
        onAddClick = model::onAddExpirationYear,
        onSubtractClick = model::onSubtractExpirationYear,
        onCreateReferral = model::onCreateReferralCode,
        onCleanReferralClick = model::onCleanReferralClick,
    )
}

@Composable
private fun ReferralCreateScreen(
    state: CreateReferralUiState,
    searchTextFieldState: TextFieldState,
    onBackPressed: () -> Unit,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubtractClick: () -> Unit,
    onCreateReferral: () -> Unit,
    onCleanReferralClick: () -> Unit,
) {
    val statusBarHeightPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val statusBarHeightDp = with(LocalDensity.current) { statusBarHeightPx.toDp() }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Create Referral",
                onBackClick = onBackPressed,
                iconRight = R.drawable.ic_info,
            )
            AnimatedVisibility(
                visible = false,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MoreInfoBox(
                    text = stringResource(R.string.referral_create_info_content),
                    title = stringResource(R.string.referral_create_info_title),
                    modifier = Modifier
                        .padding(start = 62.dp, end = 8.dp)
                        .offset(y = statusBarHeightDp)
                        .wrapContentSize()
                        .clickable(onClick = {})
                )
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                    text = "Pick Referral Code",
                    textAlign = TextAlign.Start,
                )

                UiSpacer(8.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isNotEmpty = searchTextFieldState.text.isNotEmpty()

                    VsTextInputField(
                        textFieldState = searchTextFieldState,
                        innerState = state.getInnerState(),
                        hint = stringResource(R.string.referral_screen_code_hint),
                        focusRequester = null, //focusRequester,
                        trailingIcon = if (state.searchStatus.isError() && isNotEmpty) {
                            R.drawable.x
                        } else {
                            null
                        },
                        onTrailingIconClick = {
                            onCleanReferralClick()
                        },
                        imeAction = ImeAction.Go,
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.weight(1f)
                    )

                    UiSpacer(8.dp)

                    VsButton(
                        label = "Search",
                        shape = RoundedCornerShape(percent = 20),
                        variant = VsButtonVariant.Primary,
                        state = VsButtonState.Enabled,
                        size = VsButtonSize.Medium,
                        onClick = onSearchClick,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }

                UiSpacer(16.dp)

                if (state.searchStatus != SearchStatusType.DEFAULT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            color = Theme.colors.text.extraLight,
                            style = Theme.brockmann.body.s.medium,
                            text = "Status",
                            textAlign = TextAlign.Start,
                        )

                        UiSpacer(1f)

                        if (state.searchStatus == SearchStatusType.IS_SEARCHING) {
                            UiPlaceholderLoader(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(80.dp)
                            )
                        } else {
                            SearchReferralTag(state.searchStatus)
                        }
                    }
                }

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )

                UiSpacer(16.dp)

                Text(
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                    text = "Set Expiration (in years)",
                    textAlign = TextAlign.Start,
                )

                UiSpacer(16.dp)

                CounterYearExpiration(
                    count = state.yearExpiration,
                    onIncrement = onAddClick,
                    onDecrement = onSubtractClick,
                )

                UiSpacer(16.dp)


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        color = Theme.colors.text.extraLight,
                        style = Theme.brockmann.body.s.medium,
                        text = "Expiration date",
                        textAlign = TextAlign.Start,
                    )

                    UiSpacer(1f)

                    Text(
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.s.medium,
                        text = state.formattedYearExpiration,
                        textAlign = TextAlign.Start,
                    )
                }

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )

                UiSpacer(16.dp)

                val fees = state.getFees()

                EstimatedNetworkFee(
                    title = "Registration Fees",
                    tokenGas = fees.tokenGas,
                    fiatGas = fees.tokenPrice,
                )

                UiSpacer(16.dp)

                EstimatedNetworkFee(
                    title = "Cost",
                    tokenGas = fees.costGas,
                    fiatGas = fees.costPrice,
                )
            }
        },
        bottomBar = {
            VsButton(
                label = "Create referral code",
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .fillMaxWidth(),
                variant = VsButtonVariant.Primary,
                state = if (state.searchStatus == SearchStatusType.SUCCESS) {
                    VsButtonState.Enabled
                } else {
                    VsButtonState.Disabled
                },
                onClick = onCreateReferral,
            )
        }
    )
}

private fun CreateReferralUiState.getFees() =
    if (this.fees is FeesReferral.Result) {
        FeesUiModel(
        tokenGas = this.fees.registrationFeesToken,
        tokenPrice = this.fees.registrationFeesPrice,
        costGas = this.fees.costFeesToken,
        costPrice = this.fees.costFeesPrice,
    )
} else {
    FeesUiModel()
}

private fun CreateReferralUiState.getInnerState() =
    when {
        this.searchStatus.isError() -> VsTextInputFieldInnerState.Error
        this.searchStatus == SearchStatusType.SUCCESS -> VsTextInputFieldInnerState.Success
        else -> VsTextInputFieldInnerState.Default
    }

@Composable
private fun SearchReferralTag(
    type: SearchStatusType,
) {
    val (color, text) = when (type) {
        SearchStatusType.VALIDATION_ERROR -> Pair(Theme.colors.alerts.error, "Invalid")
        SearchStatusType.SUCCESS -> Pair(Theme.colors.alerts.success, "Available")
        SearchStatusType.ERROR -> Pair(Theme.colors.alerts.error, "Already Taken")
        else -> Pair(Theme.colors.alerts.error, "Unknown")
    }

    Box(
        modifier = Modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            style = Theme.brockmann.supplementary.footnote,
        )
    }
}

@Composable
fun CounterYearExpiration(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { if (count != 1) onDecrement() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme.colors.backgrounds.secondary,
                contentColor = Theme.colors.text.primary
            ),
            modifier = Modifier
                .weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.circle_minus),
                contentDescription = "Decrease"
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.m.medium,
            )
        }

        Button(
            onClick = { if (count < 100) onIncrement() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme.colors.backgrounds.secondary,
                contentColor = Theme.colors.text.primary
            ),
            modifier = Modifier
                .weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.circle_plus),
                contentDescription = "Increase"
            )
        }
    }
}

@Composable
internal fun EstimatedNetworkFee(
    title: String,
    tokenGas: String,
    fiatGas: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .weight(1f),
        ) {
            if (tokenGas.isEmpty()) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(16.dp)
                        .width(80.dp)
                )

                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(16.dp)
                        .width(80.dp)
                )
            } else {
                Text(
                    text = tokenGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                Text(
                    text = fiatGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
                )
            }
        }
    }
}

@Preview
@Composable
internal fun SwapFormScreenPreview() {
    ReferralCreateScreen(
        state = CreateReferralUiState(),
        searchTextFieldState = TextFieldState(),
        onBackPressed = {},
        onSearchClick = {},
        onAddClick = {},
        onSubtractClick = {},
        onCreateReferral = {},
        onCleanReferralClick = {},
    )
}

private data class FeesUiModel(
    val tokenGas: String = "",
    val tokenPrice: String = "",
    val costGas: String = "",
    val costPrice: String = "",
)