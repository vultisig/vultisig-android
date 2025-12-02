package com.vultisig.wallet.ui.screens.v2.customtoken.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.loading.V2Loading
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun LoadingSearchCustomToken() {
    V2Container(
        type = ContainerType.TERTIARY,
        cornerType = CornerType.RoundedCornerShape(size = 24.dp),
        borderType = ContainerBorderType.Bordered(color = Theme.v2.colors.border.normal)
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Companion.CenterHorizontally
        ) {

            V2Loading()

            UiSpacer(
                size = 8.dp
            )
            Text(
                text = stringResource(R.string.custom_token_screen_finding_token),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.supplementary.footnote

            )
        }
    }
}

@Preview
@Composable
private fun PreviewLoadingSearchCustomToken(){
    LoadingSearchCustomToken()
}