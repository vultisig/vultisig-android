package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.R.drawable
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.models.keygen.KeygenSetupViewModel
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun Setup(
    navController: NavHostController,
    vaultId: String,
    viewModel: KeygenSetupViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val helpLink = stringResource(R.string.link_docs_create_vault)

    val textColor = Theme.colors.neutral0

    val state by viewModel.uiModel.collectAsState()

    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.setup_title),
        endIcon = drawable.question,
        onEndIconClick = {
            uriHandler.openUri(helpLink)
        },
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.setup_select_your_vault_type),
                color = textColor,
                style = Theme.menlo.body2,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            UiSpacer(size = 24.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.tabs.forEachIndexed { index, tab ->
                    GradientButton(
                        text = tab.title.asString(),
                        isSelected = state.tabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            UiSpacer(size = 24.dp)

            Text(
                text = state.tabs[state.tabIndex].content.asString(),
                color = textColor,
                style = Theme.montserrat.body3,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Image(
                painter = painterResource(id = state.tabs[state.tabIndex].drawableResId),
                contentDescription = "devices",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp)
            )

            UiSpacer(size = 24.dp)

            MultiColorButton(
                text = stringResource(R.string.setup_start),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = 44.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    )
            ) {
                if (vaultId == Destination.KeygenFlow.DEFAULT_NEW_VAULT || vaultId.isEmpty()) {
                    navController.navigate(Destination.NamingVault(VaultSetupType.fromInt(state.tabIndex)).route)
                } else {
                    // when reshare , we need to set to M_OF_N
                    navController.navigate(
                        Destination.KeygenFlow(
                            vaultId,
                            VaultSetupType.M_OF_N
                        ).route
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = if (isSelected)
            Theme.colors.oxfordBlue800
        else
            Theme.colors.turquoise800,
        style = Theme.montserrat.subtitle1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.background(
                        brush = Brush.vultiGradient(),
                        shape = RoundedCornerShape(30.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.vultiGradient(),
                        shape = RoundedCornerShape(30.dp),
                    )
                }
            )
            .padding(
                vertical = 12.dp,
                horizontal = 24.dp,
            ),
    )
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    val navController = rememberNavController()
    Setup(navController, "vaultId")
}