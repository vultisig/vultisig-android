package com.vultisig.wallet.ui.components.referral

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun VsPromoBox(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Theme.colors.backgrounds.neutral,
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(
                all = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UiIcon(
            drawableResId = icon,
            size = 20.dp,
            tint = Theme.colors.primary.accent4,
        )

        UiSpacer(12.dp)

        Column {
            Text(
                text = title,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.supplementary.footnote,
            )

            Text(
                text = description,
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.supplementary.captionSmall,
            )
        }
    }
}

@Composable
fun VsPromoTag(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(start = 16.dp)
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(
                    topEnd = 50.dp,
                    bottomEnd = 50.dp,
                    topStart = 0.dp,
                    bottomStart = 0.dp
                )
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(
                    topEnd = 50.dp,
                    bottomEnd = 50.dp,
                    topStart = 0.dp,
                    bottomStart = 0.dp
                ),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UiIcon(
            drawableResId = icon,
            size = 14.dp,
            tint = Theme.colors.primary.accent4,
        )

        UiSpacer(8.dp)

        Text(
            text = text,
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
private fun ReferralBoxes() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UiSpacer(60.dp)

        VsPromoTag(
            icon = R.drawable.ic_cup,
            text = stringResource(R.string.promo_box_referral_program)
        )

        UiSpacer(32.dp)

        VsPromoBox(
            icon = R.drawable.ic_cup,
            title = stringResource(R.string.promo_box_create_your_referral_code),
            description = stringResource(R.string.promo_box_pick_a_short_code_and_set_your_reward_payout)
        )

        UiSpacer(32.dp)

        VsPromoBox(
            icon = R.drawable.ic_cup,
            title = stringResource(R.string.promo_box_share_with_friends),
            description = stringResource(R.string.promo_box_invite_friends_to_use_your_code_while_swapping)
        )
    }
}
