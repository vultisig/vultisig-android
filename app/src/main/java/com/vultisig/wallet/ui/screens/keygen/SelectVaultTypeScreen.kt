
package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
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
        onPairClick = model::pair,
    )
}

@Composable
private fun SelectVaultTypeScreen(
    navController: NavController,
    state: SelectVaultTypeUiModel,
    onTabClick: (index: Int) -> Unit,
    onStartClick: () -> Unit,
    onPairClick: () -> Unit,
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
            /* fast&active vaults are temporarily disabled
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
             */

            UiSpacer(8.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Theme.colors.oxfordBlue400,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(all = 10.dp)
                )

                UiSpacer(8.dp)

                Text(
                    text = stringResource(R.string.select_vault_type_secure_vault_title),
                    color = textColor,
                    style = Theme.montserrat.subtitle1,
                )
            }

            // val selected = state.types[state.selectedTypeIndex]

            Image(
                painter = painterResource(id = R.drawable.vault_type_secure),
                contentDescription = "devices",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        vertical = 16.dp,
                        horizontal = 56.dp,
                    )
            )

            UiSpacer(size = 24.dp)

            /* fast&active vaults are temporarily disabled
            Text(
                text = selected.description.asString(),
                color = textColor,
                style = Theme.montserrat.body3,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            */

            Column {
                TextPoint(
                    text = stringResource(R.string.select_vault_type_secure_vault_point_0)
                )
                TextPoint(
                    text = stringResource(R.string.select_vault_type_secure_vault_point_1)
                )
                TextPoint(
                    text = stringResource(R.string.select_vault_type_secure_vault_point_2)
                )
            }

            UiSpacer(size = 40.dp)

            MultiColorButton(
                text = stringResource(id = R.string.select_vault_type_start),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = 44.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                    ),
                onClick = onStartClick,
            )

            // if (selected.hasPair) {
            UiSpacer(size = 12.dp)

            MultiColorButton(
                text = stringResource(R.string.select_vault_type_pair_action),
                backgroundColor = Theme.colors.oxfordBlue800,
                textColor = Theme.colors.turquoise800,
                iconColor = Theme.colors.oxfordBlue800,
                borderSize = 1.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                    ),
                onClick = onPairClick,
            )
            //}

            UiSpacer(size = 12.dp)
        }
    }
}

@Composable
private fun TextPoint(
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = Theme.colors.turquoise600Main,
            modifier = Modifier.size(24.dp),
        )

        UiSpacer(12.dp)

        Text(
            text = text,
            color = Theme.colors.neutral0,
            style = Theme.montserrat.body1,
        )
    }
}

@Preview
@Composable
private fun SelectVaultTypeScreenPreview() {
    SelectVaultTypeScreen(
        navController = rememberNavController(),
        state = SelectVaultTypeUiModel(selectedTypeIndex = 0),
        onTabClick = {},
        onStartClick = {},
        onPairClick = {},
    )
}