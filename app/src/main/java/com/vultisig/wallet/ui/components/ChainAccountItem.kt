package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
@Composable
internal fun ChainAccountItem(
    account: AccountUiModel,
    isRearrangeMode: Boolean,
    isBalanceVisible: Boolean,
    onCopy: (String) -> Unit,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val longClick = {

        VsClipboardService.copy(context, account.address)

        onCopy(
            context.getString(
                R.string.chain_account_item_address_copied,
                account.address
            )
        )
    }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.oxfordBlue600Main,
        ),
        modifier = Modifier
            .combinedClickable(
                onClick = clickOnce(onClick),
                onLongClick = if (isRearrangeMode) null else longClick
            )
            .fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(all = 12.dp),
        ) {
            AnimatedContent(
                targetState = isRearrangeMode,
                label = "isRearrangeMode",
            ) { isRearrangeModeEnabled ->
                if (isRearrangeModeEnabled)
                    Icon(
                        painter = painterResource(id = R.drawable.hamburger_menu),
                        contentDescription = "rearrange icon",
                        tint = Theme.colors.neutral0,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp)
                    )
            }


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
                        ToggleVisibilityText(
                            isVisible = isBalanceVisible,
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
                                ToggleVisibilityText(
                                    isVisible = isBalanceVisible,
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
                            ToggleVisibilityText(
                                isVisible = isBalanceVisible,
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

                MiddleEllipsisText(
                    text = if (isBalanceVisible)
                        account.address
                    else "************",
                    style = Theme.montserrat.body1,
                    color = Theme.colors.turquoise600Main,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewChainAccountItem() {
    Column {
        ChainAccountItem(
            isRearrangeMode = true,
            isBalanceVisible = true,
            onCopy = {},
            account = AccountUiModel(
                chainName = "Bitcoin",
                logo = R.drawable.bitcoin,
                address = "123abc456bca123abc456bca123abc456bca",
                nativeTokenAmount = "0.01",
                fiatAmount = "1000$",
                assetsSize = 4,
                model = Address(
                    chain = Chain.Bitcoin,
                    address = "123abc456bca123abc456bca123abc456bca",
                    accounts = emptyList()
                )
            )
        )

        UiSpacer(size = 10.dp)

        ChainAccountItem(
            isRearrangeMode = true,
            isBalanceVisible = false,
            onCopy = {},
            account = AccountUiModel(
                chainName = "Ethereum",
                logo = R.drawable.ethereum,
                address = "0x123abc456bca123abc456bca123abc456bca",
                nativeTokenAmount = "0.01",
                fiatAmount = "999$",
                assetsSize = 4,
                model = Address(
                    chain = Chain.Bitcoin,
                    address = "123abc456bca123abc456bca123abc456bca",
                    accounts = emptyList()
                )
            )
        )
    }
}