package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.ItemAccountUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainAccountItem(
    account: ItemAccountUiModel,
    onClick: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.oxfordBlue600Main,
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
                        style = Theme.montserrat.subtitle1,
                        color = Theme.colors.neutral100,
                        modifier = Modifier
                            .weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    UiSpacer(size = 12.dp)

                    if (account.assetsSize > 1) {
                        Text(
                            text = stringResource(
                                R.string.vault_accounts_account_assets,
                                account.assetsSize
                            ),
                            style = Theme.menlo.body1,
                            color = Theme.colors.neutral100,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(
                                    color = Theme.colors.oxfordBlue400,
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 4.dp,
                                )
                        )
                    } else {
                        AnimatedContent(
                            targetState = account.nativeTokenAmount,
                            label = "ChainAccount NativeTokenAmount"
                        ) { nativeTokenAmount ->
                            if (nativeTokenAmount != null) {
                                Text(
                                    text = nativeTokenAmount,
                                    style = Theme.menlo.body1,
                                    color = Theme.colors.neutral100,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                UiPlaceholderLoader(
                                    modifier = Modifier
                                        .width(48.dp)
                                )
                            }
                        }
                    }

                    UiSpacer(12.dp)

                    AnimatedContent(
                        targetState = account.fiatAmount,
                        label = "ChainAccount FiatAmount"
                    ) { fiatAmount ->
                        if (fiatAmount != null) {
                            Text(
                                text = fiatAmount,
                                style = Theme.montserrat.subtitle1,
                                color = Theme.colors.neutral100,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            UiPlaceholderLoader(
                                modifier = Modifier
                                    .width(48.dp)
                            )
                        }
                    }

                }

                UiSpacer(14.dp)

                Text(
                    text = account.address,
                    style = Theme.montserrat.body1,
                    color = Theme.colors.turquoise600Main,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewChainAccountItem() {
    ChainAccountItem(
        ItemAccountUiModel(
            chainName = "Bitcoin",
            logo = R.drawable.bitcoin,
            address = "123abc456bca123abc456bca123abc456bca",
            nativeTokenAmount = "0.01",
            fiatAmount = "1000$",
            assetsSize = 4,
            addressId = "123abc456bca123abc456bca123abc456bca"
        )
    )
}