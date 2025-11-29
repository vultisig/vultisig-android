package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SettingsItem(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    colorTint: Color? = null,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .clickOnce(onClick = onClick)
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.v2.colors.backgrounds.secondary
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .padding(
                        end = 12.dp,
                    )
                    .size(20.dp),
                painter = painterResource(id = icon),
                contentDescription = stringResource(R.string.token_logo),
                tint = colorTint ?: Theme.v2.colors.neutrals.n100,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = colorTint ?: Theme.v2.colors.neutrals.n100,
                    style = Theme.montserrat.body2,
                )
                Text(
                    text = subtitle,
                    color = colorTint ?: Theme.v2.colors.neutrals.n300,
                    style = Theme.menlo.overline2,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                painter = painterResource(id = R.drawable.ic_small_caret_right),
                contentDescription = null,
                tint = colorTint ?: Theme.v2.colors.neutrals.n100,
            )
        }
    }
}

@Preview
@Composable
private fun SettingsItemPreview() {
    SettingsItem(
        title = "Title",
        subtitle = "Subtitle",
        icon = R.drawable.icon_qr
    )
}