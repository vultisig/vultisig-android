package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.theme.Theme

internal data class AccountItemUiModel(
    val assetSize: Int,
    val chainName: String,
    val address: String,
    val nativeTokenAmount: String,
    val fiatAmount: String,
    val imageModel: ImageModel,
)

@Composable
internal fun AccountItem(
    modifier: Modifier = Modifier,
    uiModel: AccountItemUiModel,
) {
    Row(
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.AccountBox,
            contentDescription = null,
        )

        Column {
            Text(
                text = uiModel.chainName,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
            )
            CopiableAddress(
                address = uiModel.address
            )
        }
        UiSpacer(
            weight = 1f
        )

        Column {
            Text(
                text = uiModel.fiatAmount,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary
            )
            Text(
                text = uiModel.assetSize.takeIf { uiModel.assetSize>0 }?.toString() ?: uiModel.nativeTokenAmount,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
            )
        }

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null
        )


    }
}

@Preview
@Composable
private fun PreviewAccountItem() {
    AccountItem(
        modifier = Modifier,
        AccountItemUiModel(
            assetSize = 2,
            chainName = "Ethereum",
            address = "0x1234567890",
            nativeTokenAmount = "3.424 VULT",
            fiatAmount = "$32,201.15",
            imageModel = Icons.Default.AccountBox
        )
    )
}

@Preview
@Composable
private fun PreviewAccounts() {
    TopShineContainer {
        LazyColumn {
            itemsIndexed(
                listOf(
                    AccountItemUiModel(
                        assetSize = 2,
                        chainName = "Ethereum",
                        address = "0x1234567890",
                        nativeTokenAmount = "3.424 VULT",
                        fiatAmount = "$32,201.15", imageModel = Icons.Default.AccountBox
                    ),
                    AccountItemUiModel(
                        assetSize = 1,
                        chainName = "Ethereum",
                        address = "0x1234567890",
                        nativeTokenAmount = "3.424 VULT",
                        fiatAmount = "$32,201.15", imageModel = Icons.Default.AccountBox
                    ),
                    AccountItemUiModel(
                        assetSize = 2,
                        chainName = "Ethereum",
                        address = "0x1234567890",
                        nativeTokenAmount = "3.424 VULT",
                        fiatAmount = "$32,201.15", imageModel = Icons.Default.AccountBox
                    )
                )
            ) { index, item ->
                if (index != 2)
                    Column {
                        AccountItem(
                            uiModel = item
                        )
                        HorizontalDivider(
                            color = Theme.colors.borders.light,
                            thickness = 1.dp,
                        )
                    }
                else AccountItem(
                    uiModel = item
                )
            }
        }
    }

}