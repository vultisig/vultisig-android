package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.ChainAccountUiModel
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.menloFamily
import com.vultisig.wallet.ui.theme.montserratFamily

@Composable
internal fun ChainAccountItem(
    account: ChainAccountUiModel,
    onClick: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appColor.oxfordBlue600Main,
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(all = 12.dp),
        ) {
            Image(
                painter = painterResource(id = account.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = account.chainName,
                        style = MaterialTheme.montserratFamily.subtitle1,
                        color = MaterialTheme.appColor.neutral100,
                        modifier = Modifier
                            .weight(1f),
                    )

                    UiSpacer(weight = 1f)

                    Text(
                        text = account.nativeTokenAmount ?: "",
                        style = MaterialTheme.menloFamily.body1,
                        color = MaterialTheme.appColor.neutral100,
                    )

                    UiSpacer(12.dp)

                    Text(
                        text = account.fiatAmount ?: "",
                        style = MaterialTheme.montserratFamily.subtitle1,
                        color = MaterialTheme.appColor.neutral100,
                    )
                }

                UiSpacer(14.dp)

                Text(
                    text = account.address,
                    style = MaterialTheme.montserratFamily.body1,
                    color = MaterialTheme.appColor.turquoise600Main,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewChainAccountItem() {
    ChainAccountItem(
        ChainAccountUiModel(
            chainName = "Bitcoin",
            logo = R.drawable.bitcoin,
            address = "123abc456bca123abc456bca123abc456bca",
            nativeTokenAmount = "0.01",
            fiatAmount = "1000$",
        )
    )
}