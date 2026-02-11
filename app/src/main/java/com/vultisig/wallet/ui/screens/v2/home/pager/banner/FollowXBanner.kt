package com.vultisig.wallet.ui.screens.v2.home.pager.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.screens.referral.SetBackgoundBanner
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks.TWITTER_ID


@Composable
internal fun FollowXBanner(
    onFollowXClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {

        SetBackgoundBanner(backgroundImageResId = R.drawable.follow_banner)

        Column(
            modifier = Modifier
                .padding(
                    all = 24.dp
                )
        ) {
            Text(
                text = stringResource(R.string.invite_to_x_banner_title),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
            UiSpacer(
                size = 2.dp
            )
            Text(
                text = stringResource(R.string.invite_to_x_banner_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(
                size = 16.dp
            )


            Text(
                text = stringResource(R.string.invite_to_x_banner_button, TWITTER_ID),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.backgrounds.primary,
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .clickOnce(onClick = onFollowXClick)
                    .background(
                        color = Theme.v2.colors.buttons.primary
                    )
                    .padding(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    )
            )
        }

    }
}


@Preview
@Composable
private fun PreviewInviteToXBanner() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
    ) {
        FollowXBanner {

        }
    }
}