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
import com.vultisig.wallet.ui.components.containers.VsContainerBorderType
import com.vultisig.wallet.ui.components.containers.VsContainerType
import com.vultisig.wallet.ui.components.containers.VsContainerCornerType
import com.vultisig.wallet.ui.components.containers.VsContainer
import com.vultisig.wallet.ui.components.loader.V2Loading
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun LoadingSearchCustomToken() {
    VsContainer(
        type = VsContainerType.TERTIARY,
        vsContainerCornerType = VsContainerCornerType.RoundedVsContainerCornerShape(size = 24.dp),
        borderType = VsContainerBorderType.Bordered(color = Theme.colors.border.normal)
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
                color = Theme.colors.text.primary,
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