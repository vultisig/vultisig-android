package com.vultisig.wallet.ui.screens.v2.home.pager.banner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks.TWITTER_ID


@Composable
internal fun FollowXBanner(
    modifier: Modifier = Modifier,
    onFollowXClick: () -> Unit,
) {

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    all = 24.dp
                )
        ) {
            Text(
                text = stringResource(R.string.invite_to_x_banner_title),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
            )
            UiSpacer(
                size = 2.dp
            )
            Text(
                text = stringResource(R.string.invite_to_x_banner_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
            )
            UiSpacer(
                size = 16.dp
            )


            Text(
                text = stringResource(R.string.invite_to_x_banner_button, TWITTER_ID),
                style = Theme.brockmann.button.medium,
                color = Theme.colors.text.button.dark,
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .clickOnce(onClick = onFollowXClick)
                    .background(
                        color = Theme.colors.aquamarine
                    )
                    .padding(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.invite_x_banner
                ),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .align(alignment = Alignment.BottomEnd)
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
                .height(140.dp)
    ) {
        FollowXBanner {

        }
    }
}