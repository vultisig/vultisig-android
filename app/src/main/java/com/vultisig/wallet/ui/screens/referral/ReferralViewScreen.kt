package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ReferralViewUiState
import com.vultisig.wallet.ui.models.referral.ViewReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralViewScreen(
    navController: NavController,
    model: ViewReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ReferralViewScreen(
        state = state,
        referralCodeState = model.referralCodeTextField,
        friendReferralCodeState = model.friendReferralCodeTextField,
        onBackPressed = navController::popBackStack,
        onClickEditReferral = { }, // This will be used to edit rewards
        onClickFriendReferralBanner = model::navigateToStoreFriendReferralBanner,
        onVaultClicked = { },
        onEditFriendReferralCode = model::navigateToStoreFriendReferralBanner,
    )
}

@Composable
internal fun ReferralViewScreen(
    state: ReferralViewUiState,
    referralCodeState: TextFieldState,
    friendReferralCodeState: TextFieldState,
    onBackPressed: () -> Unit,
    onClickFriendReferralBanner: () -> Unit,
    onClickEditReferral: () -> Unit,
    onVaultClicked: () -> Unit,
    onEditFriendReferralCode: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Referral",
                onBackClick = {
                    onBackPressed()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                if (state.referralFriendCode.isEmpty()) {
                    FriendReferralBanner(
                        onClick = onClickFriendReferralBanner
                    )
                } else {
                    Text(
                        text = "Your Friend Referral Code",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.primary,
                    )

                    UiSpacer(8.dp)

                    VsTextInputField(
                        textFieldState = friendReferralCodeState,
                        innerState = VsTextInputFieldInnerState.Default,
                        enabled = false,
                        focusRequester = null,
                        trailingIcon = R.drawable.ic_edit_pencil,
                        onTrailingIconClick = {
                            onEditFriendReferralCode()
                        },
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                UiSpacer(16.dp)

                Text(
                    text = "Vault Selected",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(16.dp)

                VaultItem(
                    name = state.vaultName,
                    onVaultClicked = onVaultClicked,
                )

                UiSpacer(16.dp)

                ReferralDataBanner()

                UiSpacer(16.dp)

                Text(
                    text = "Your referral code",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(8.dp)

                VsTextInputField(
                    textFieldState = referralCodeState,
                    innerState = VsTextInputFieldInnerState.Default,
                    enabled = false,
                    focusRequester = null,
                    trailingIcon = R.drawable.ic_paste,
                    onTrailingIconClick = {
                    },
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.fillMaxWidth()
                )

                UiSpacer(16.dp)

                ReferralExpirationItem()

                UiSpacer(16.dp)

                VsButton(
                    label = "Edit referral",
                    modifier = Modifier.fillMaxWidth(),
                    variant = VsButtonVariant.Primary,
                    state = VsButtonState.Enabled,
                    onClick = onClickEditReferral,
                )
            }
        },
        bottomBar = {
            UiSpacer(32.dp)
        },
    )
}

@Composable
private fun ReferralExpirationItem(expiration: String = "") {
    Column(
        modifier = Modifier
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = Theme.colors.borders.light
                ),
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.backgrounds.secondary)
            .fillMaxWidth()
            .padding(all = 16.dp),
    ) {
        Text(
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.body.s.medium,
            text = "Expires on"
        )
        Text(
            color = Theme.colors.text.primary,
            style = Theme.brockmann.body.l.medium,
            text = "25 May of 20027"
        )
    }
}

@Composable
private fun FriendReferralBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.referral_friend_banner),
            contentDescription = "Provider Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append("Save ")
                    withStyle(style = SpanStyle(color = Theme.colors.primary.accent4)) {
                        append("10%")
                    }
                    append(" on swaps now")
                },
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add a Friends Referral",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )
        }
    }
}

@Composable
private fun ReferralDataBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.referral_data_banner),
            contentDescription = "Provider Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_cup,
                size = 24.dp,
                tint = Theme.colors.primary.accent4,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Collected Rewards",
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )

            Text(
                text = "250.40 RUNE",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
            )
        }
    }
}

@Composable
fun VaultItem(
    name: String,
    onVaultClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Theme.colors.backgrounds.secondary)
            .clickable { onVaultClicked() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        SubcomposeAsyncImage(
            model = R.drawable.referral_vault_avatar,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )

        UiSpacer(12.dp)

        Text(
            text = name,
            color = Theme.colors.text.primary,
            style = Theme.brockmann.body.m.regular,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "click_referral_vault",
            tint = Theme.colors.text.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
