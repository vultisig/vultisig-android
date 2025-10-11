package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenSelectionItem(
    title: String,
    subtitle: String,
    logo: Any,
    @DrawableRes chainLogo: Int? = null,
    hasTokenSwitch: Boolean = true,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.oxfordBlue600Main
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(all = 12.dp)
                .clickable { onCheckedChange?.invoke(!isChecked) },
        ) {
            Box {
                val tokenLogoModifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                TokenLogo(
                    errorLogoModifier = tokenLogoModifier
                        .background(Theme.colors.neutral100),
                    logo = logo,
                    title = title,
                    modifier = tokenLogoModifier
                )
                if (chainLogo != null)
                    Image(
                        painter = painterResource(id = chainLogo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .border(
                                width = 1.dp,
                                color = Theme.colors.neutral0,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .align(Alignment.BottomEnd)
                    )
            }

            UiSpacer(size = 10.dp)

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    color = Theme.colors.neutral100,
                    style = Theme.montserrat.subtitle1,
                )
                Text(
                    text = subtitle,
                    color = Theme.colors.neutral100,
                    style = Theme.montserrat.body3,
                )
            }

            if (hasTokenSwitch) {
                VsSwitch(
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Theme.colors.neutral0,
                        checkedBorderColor = Theme.colors.turquoise800,
                        checkedTrackColor = Theme.colors.turquoise800,
                        uncheckedThumbColor = Theme.colors.neutral0,
                        uncheckedBorderColor = Theme.colors.oxfordBlue400,
                        uncheckedTrackColor = Theme.colors.oxfordBlue400
                    ),
                    checked = isChecked,
                    onCheckedChange = null,
                )
            }
        }
    }
}

@Preview
@Composable
fun TokenSelectionItemPreview() {
    TokenSelectionItem(
        title = "ETH",
        subtitle = "Ethereum",
        logo = R.drawable.ethereum,
        chainLogo = R.drawable.base,
    )
}