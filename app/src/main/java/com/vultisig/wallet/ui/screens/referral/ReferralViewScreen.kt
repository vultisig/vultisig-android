package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ViewReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralViewScreen(
    navController: NavController,
    model: ViewReferralViewModel = hiltViewModel(),
) {
    ReferralViewScreen(
        onBackPressed = navController::popBackStack,
        onClickEditReferral = {}
    )
}

@Composable
internal fun ReferralViewScreen(
    onBackPressed: () -> Unit,
    onClickEditReferral: () -> Unit,
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
                FriendReferralBanner()

                UiSpacer(16.dp)

                Text(
                    text = "Vault Selected",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(1f)

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
private fun FriendReferralBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
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