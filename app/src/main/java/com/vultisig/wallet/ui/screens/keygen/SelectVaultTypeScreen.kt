package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.GradientButton
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.keygen.SelectVaultTypeUiModel
import com.vultisig.wallet.ui.models.keygen.SelectVaultTypeViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SelectVaultTypeScreen(
    navController: NavController,
    model: SelectVaultTypeViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    SelectVaultTypeScreen(
        navController = navController,
        state = state,
        onTabClick = model::selectTab,
        onStartClick = model::start,
    )
}

@Composable
private fun SelectVaultTypeScreen(
    navController: NavController,
    state: SelectVaultTypeUiModel,
    onTabClick: (index: Int) -> Unit,
    onStartClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val helpLink = stringResource(R.string.link_docs_create_vault)

    val textColor = Theme.colors.neutral0

    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.setup_title),
        endIcon = R.drawable.question,
        onEndIconClick = {
            uriHandler.openUri(helpLink)
        },
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.types.forEachIndexed { index, tab ->
                    GradientButton(
                        text = tab.title.asString(),
                        isSelected = state.selectedTypeIndex == index,
                        onClick = { onTabClick(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val selected = state.types[state.selectedTypeIndex]

            Image(
                painter = painterResource(id = selected.drawableResId),
                contentDescription = "devices",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Text(
                text = selected.description.asString(),
                color = textColor,
                style = Theme.montserrat.body3,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            UiSpacer(size = 24.dp)

            MultiColorButton(
                text = selected.startTitle.asString(),
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
                    ),
                onClick = onStartClick,
            )
        }
    }
}

@Preview
@Composable
private fun SelectVaultTypeScreenPreview() {
    SelectVaultTypeScreen(
        navController = rememberNavController(),
        state = SelectVaultTypeUiModel(),
        onTabClick = {},
        onStartClick = {},
    )
}