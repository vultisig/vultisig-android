package com.vultisig.wallet.ui.components.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.screens.v2.home.components.AnimatedPrice
import com.vultisig.wallet.ui.screens.v2.home.components.CopiableAddress
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AccountItem(
    modifier: Modifier = Modifier,
    account: AccountUiModel,
    isBalanceVisible: Boolean,
    onCopy: (String) -> Unit,
    onClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickOnce(onClick = onClick)
            .height(intrinsicSize = IntrinsicSize.Min),
    ) {
        Image(
            painter = painterResource(id = account.logo),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
        )

        UiSpacer(
            size = 12.dp
        )

        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = account.chainName,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            CopiableAddress(
                address = account.address,
                modifier = Modifier
                    .padding(top = 4.dp),
                onAddressCopied = onCopy,
            )
        }

        UiSpacer(
            weight = 1f
        )

        Column(
            modifier = Modifier
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            AnimatedPrice(
                totalFiatValue = account.fiatAmount,
                style = Theme.satoshi.price.bodyS,
                color = Theme.colors.text.primary,
                isVisible = isBalanceVisible,
            )

            if (account.assetsSize > 1) {
                ToggleVisibilityText(
                    isVisible = isBalanceVisible,
                    text = stringResource(
                        R.string.vault_accounts_account_assets,
                        account.assetsSize
                    ),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.colors.text.extraLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                AnimatedContent(
                    targetState = account.nativeTokenAmount,
                    label = "ChainAccount NativeTokenAmount"
                ) { nativeTokenAmount ->
                    if (nativeTokenAmount != null) {
                        ToggleVisibilityText(
                            isVisible = isBalanceVisible,
                            text = nativeTokenAmount,
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.colors.text.extraLight,
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

        }
    }

}
