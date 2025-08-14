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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralCreateScreen(
    navController: NavController,
    model: CreateReferralViewModel = hiltViewModel(),
) {
    ReferralCreateScreen(
        onBackPressed = navController::popBackStack,
        onSearchClick = {},
        onAddClick = model::onAddExpirationYear,
        onSubtractClick = model::onSubtractExpirationYear,
        onCreateReferral = model::onCreateReferralCode,
    )
}

@Composable
private fun ReferralCreateScreen(
    onBackPressed: () -> Unit,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubtractClick: () -> Unit,
    onCreateReferral: () -> Unit,
) {
    val statusBarHeightPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val statusBarHeightDp = with(LocalDensity.current) { statusBarHeightPx.toDp() }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Create Referral",
                onBackClick = onBackPressed,
                iconRight = R.drawable.ic_question_mark,
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
                    VsTextInputField(
                        textFieldState = TextFieldState(),
                        innerState = VsTextInputFieldInnerState.Default,
                        hint = stringResource(R.string.referral_screen_code_hint),
                        focusRequester = null, //focusRequester,
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
                    )
                }

                UiSpacer(16.dp)

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

                    SearchReferralTag()
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

                CounterYearExpiration(1, {}, {})

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
                        text = "21 June 2025",
                        textAlign = TextAlign.Start,
                    )
                }

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )
            }
        },
        bottomBar = {
            VsButton(
                label = "Create referral code",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp).fillMaxWidth(),
                variant = VsButtonVariant.Primary,
                state = VsButtonState.Disabled,
                onClick = onCreateReferral,
            )
        }
    )
}

@Composable
private fun SearchReferralTag(
    text: String = "Available",
    color: Color = Theme.colors.alerts.success,
){
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
            text = "Available",
            color = Theme.colors.alerts.success,
            style = Theme.brockmann.supplementary.footnote,
        )
    }
}

@Composable
fun CounterYearExpiration(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onDecrement,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme.colors.backgrounds.secondary,
                contentColor = Theme.colors.text.primary
            ),
            modifier = Modifier.weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
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
            onClick = onIncrement,
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
                imageVector = Icons.Default.Add,
                contentDescription = "Increase"
            )
        }
    }
}
