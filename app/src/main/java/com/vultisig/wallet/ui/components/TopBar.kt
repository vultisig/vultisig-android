package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.ClickOnce
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.closestActivityOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopBar(
    navController: NavController,
    centerText: String,
    modifier: Modifier = Modifier,
    @DrawableRes startIcon: Int? = null,
    @DrawableRes endIcon: Int? = null,
    onStartIconClick: (() -> Unit)? = null,
    onEndIconClick: () -> Unit = {},
) {
    val activity = LocalContext.current.closestActivityOrNull
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = centerText,
                style = Theme.montserrat.heading5,
                fontWeight = FontWeight.Bold,
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Theme.colors.oxfordBlue800,
            titleContentColor = Theme.colors.neutral0
        ),
        navigationIcon = {
            startIcon?.let {
                IconButton(
                    onClick = ClickOnce {
                        onStartIconClick?.invoke() ?: run {
                            if (!navController.popBackStack())
                                activity?.finish()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        tint = Theme.colors.neutral0,
                    )
                }
            }
        },
        actions = {
            endIcon?.let {
                IconButton(
                    onClick = ClickOnce { onEndIconClick }
                ) {
                    Icon(
                        painter = painterResource(id = endIcon),
                        contentDescription = null,
                        tint = Theme.colors.neutral0,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Preview
@Composable
private fun TopBarPreview() {
    TopBar(
        navController = rememberNavController(),
        centerText = "Title",
        startIcon = R.drawable.caret_left,
    )
}