package com.vultisig.wallet.ui.screens.keygen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.keygen.KeygenRoleViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeygenRoleScreen(
    navController: NavController,
    model: KeygenRoleViewModel = hiltViewModel(),
) {
    KeygenRoleScreen(
        navController = navController,
        onInitiateClick = model::initiate,
        onPairClick = model::pair,
        onImportClick = model::import
    )
}

@Composable
private fun KeygenRoleScreen(
    navController: NavController,
    onInitiateClick: () -> Unit = {},
    onPairClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
) {
    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
        topBar = {
            TopBar(
                navController = navController,
                startIcon = R.drawable.caret_left,
                centerText = stringResource(R.string.keygen_role_title)
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(it)
                    .padding(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RoleOption(
                    drawableResId = R.drawable.ic_initiate,
                    title = stringResource(R.string.keygen_role_initiate_title),
                    action = stringResource(R.string.keygen_role_initiate_action),
                    onClick = onInitiateClick,
                    isSecondaryAction = false,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OrDivider(
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = stringResource(R.string.keygen_role_or),
                        color = Theme.colors.neutral100,
                        style = Theme.menlo.subtitle1,
                        textAlign = TextAlign.Center,
                    )

                    OrDivider(
                        modifier = Modifier.weight(1f)
                    )
                }

                RoleOption(
                    drawableResId = R.drawable.ic_pair,
                    title = stringResource(R.string.keygen_role_pair_title),
                    action = stringResource(R.string.keygen_role_pair_action),
                    isSecondaryAction = true,
                    onClick = onPairClick,
                    modifier = Modifier.weight(1f),
                )

                MultiColorButton(
                    text = stringResource(R.string.home_screen_import_vault),
                    backgroundColor = Theme.colors.oxfordBlue800,
                    textColor = Theme.colors.turquoise800,
                    iconColor = Theme.colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .padding(
                            all = 16.dp,
                        ),
                    onClick = onImportClick,
                )
            }
        }
    )
}

@Composable
private fun OrDivider(
    modifier: Modifier = Modifier,
) {
    UiHorizontalDivider(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Theme.colors.transparentWhite,
                Theme.colors.neutral700,
                Theme.colors.transparentWhite,
            )
        ),
        modifier = modifier,
    )
}

@Composable
private fun RoleOption(
    @DrawableRes drawableResId: Int,
    title: String,
    action: String,
    isSecondaryAction: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = Theme.colors.oxfordBlue600Main,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(all = 24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = drawableResId),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp),
            )

            UiSpacer(size = 24.dp)

            Text(
                text = stringResource(R.string.keygen_role_option_caption),
                color = Theme.colors.neutral300,
                style = Theme.menlo.body1,
                textAlign = TextAlign.Center,
            )

            Text(
                text = title,
                color = Theme.colors.neutral0,
                style = Theme.montserrat.heading5,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 36.dp)
        }

        if (isSecondaryAction) {
            MultiColorButton(
                text = action,
                backgroundColor = Theme.colors.oxfordBlue600Main,
                textColor = Theme.colors.turquoise800,
                iconColor = Theme.colors.oxfordBlue800,
                borderSize = 1.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onClick,
            )
        } else {
            MultiColorButton(
                minHeight = 44.dp,
                text = action,
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onClick,
            )
        }
    }
}

@Preview
@Composable
private fun KeygenRoleScreenPreview() {
    KeygenRoleScreen(
        navController = rememberNavController(),
        onPairClick = {},
        onInitiateClick = {}
    )
}