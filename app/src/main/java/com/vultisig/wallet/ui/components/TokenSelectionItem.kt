package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.montserratFamily

@Composable
internal fun TokenSelectionItem(
    title: String,
    subtitle: String,
    @DrawableRes logo: Int,
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appColor.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .padding(
                        end = 12.dp,
                    )
                    .size(32.dp)
                    .clip(CircleShape),
                painter = painterResource(id = logo),
                contentDescription = stringResource(R.string.token_logo),
                contentScale = ContentScale.Crop
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.appColor.neutral100,
                    style = MaterialTheme.montserratFamily.subtitle1,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.appColor.neutral100,
                    style = MaterialTheme.montserratFamily.body3,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Switch(
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.appColor.neutral0,
                    checkedBorderColor = MaterialTheme.appColor.turquoise800,
                    checkedTrackColor = MaterialTheme.appColor.turquoise800,
                    uncheckedThumbColor = MaterialTheme.appColor.neutral0,
                    uncheckedBorderColor = MaterialTheme.appColor.oxfordBlue400,
                    uncheckedTrackColor = MaterialTheme.appColor.oxfordBlue400
                ),
                checked = isChecked,
                onCheckedChange = { isChecked ->
                    onCheckedChange?.invoke(isChecked)
                },
            )
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
        isChecked = false,
    )
}