package com.vultisig.wallet.presenter.tokens.item

import androidx.compose.foundation.Image
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins

@Composable
fun TokenSelectionItem(
    token: Coin,
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.dimens.small1),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appColor.oxfordBlue600Main
        )
    ) {
        Row(Modifier.padding(12.dp)) {
            Image(
                modifier = Modifier
                    .padding(
                        start = 16.dp,
                        top = 20.dp,
                        bottom = 20.dp
                    )
                    .size(32.dp)
                    .clip(CircleShape),
                painter = getCoinLogo(logoName = token.logo),
                contentDescription = stringResource(R.string.token_logo),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(
                    start = 12.dp
                )
            ) {
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = token.ticker,
                    color = MaterialTheme.appColor.neutral100,
                    style = MaterialTheme.montserratFamily.titleLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = token.priceProviderID,
                    color = MaterialTheme.appColor.neutral100,
                    style = MaterialTheme.montserratFamily.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Switch(
                modifier = Modifier
                    .padding(
                        top = 12.dp,
                        end = 12.dp
                    ),
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

@Composable
private fun getCoinLogo(logoName: String): Painter {
    val coinLogoID = Coins.getCoinLogo(logoName = logoName)
    return painterResource(id = coinLogoID)
}


@Preview
@Composable
fun TokenSelectionItemPreview() {
    TokenSelectionItem(
        token = Coins.SupportedCoins[0],
        isChecked = false,
    )
}