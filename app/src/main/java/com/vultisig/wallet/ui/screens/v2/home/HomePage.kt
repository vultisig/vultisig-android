package com.vultisig.wallet.ui.screens.v2.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.scaffold.ExpandableTopbarScrollBehavior
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.scaffold.rememberExpandableTopbarScrollState
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.screens.v2.home.components.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.ChooseVaultButton
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButton
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButtonType
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

@Preview
@Composable
internal fun HomePage() {
    val lazyListState = rememberLazyListState()
    val isTopbarCollapsed by rememberExpandableTopbarScrollState(
        lazyListState = lazyListState,
        scrollBehavior = ExpandableTopbarScrollBehavior.EXPAND_WHEN_FIRST_ITEM_VISIBLE
    )
    val coroutineScope = rememberCoroutineScope()
    val snackbarState = rememberVsSnackbarState()

    var isBottomSheetVisible by remember {
        mutableStateOf(false)
    }


    ScaffoldWithExpandableTopBar(
        snackbarState = snackbarState,
        isTopbarCollapsed = isTopbarCollapsed,
        topBarCollapsedContent = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Theme.colors.backgrounds.primary)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChooseVaultButton(
                        vaultName = "Main Vault",
                        isFastVault = false,
                    )

                    Column(
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "Portfolio Balance",
                            color = Theme.colors.text.extraLight,
                            style = Theme.brockmann.body.s.medium
                        )
                        UiSpacer(
                            size = 2.dp
                        )
                        Text(
                            text = "$453,010.77",
                            style = Theme.satoshi.price.bodyS,
                            color = Theme.colors.text.primary
                        )
                    }
                }
                UiHorizontalDivider()

                UiHorizontalDivider(
                    color = Theme.colors.borders.light
                )
            }
        },
        topBarExpandedContent = {
            val context = LocalContext.current
            val displayMetrics = context.resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Theme.colors.primary.accent3,
                                Theme.colors.backgrounds.primary
                            ),
                            radius = screenWidthPx,
                            center = androidx.compose.ui.geometry.Offset(
                                screenWidthPx /2,
                                -screenWidthPx * 0.65f
                            )
                        )
                    )
                    .padding(all = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopRow()
                UiSpacer(
                    40.dp
                )
                BalanceBanner(
                    isVisible = true,
                    balance = "$53,010.77",
                ) { }

                UiSpacer(32.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
                ) {
                    TransactionTypeButtonType.entries.forEach {
                        TransactionTypeButton(
                            txType = it,
                            isSelected = it == TransactionTypeButtonType.SEND,
                        )
                    }
                }

                UiSpacer(
                    size = 32.dp
                )

            }
        },


        bottomBarContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Theme.colors.backgrounds.primary.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .align(Alignment.BottomCenter),
                )

                Row(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Aa")
                    UiSpacer(
                        weight = 1f
                    )
                    CameraButton {  }
                }
            }
        },
        content = {
            Box{

                Column(
                    modifier = Modifier
                        .background(Theme.colors.backgrounds.primary)
                        .fillMaxSize()
                ) {

                    Text(
                        "Aa", modifier = Modifier
                            .padding(16.dp)
                            .clickable(onClick = {
                                coroutineScope.launch {
                                    snackbarState.show("Bitcoin address copied")
                                }
                            })
                    )

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 64.dp)
                    ) {
                        items(50) { index ->
                            Text(
                                "Item #$index",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }

                    if (isBottomSheetVisible) {
                        VsModalBottomSheet({
                            isBottomSheetVisible = false
                        }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FloatingActionButton(onClick = {
                                    coroutineScope.launch {
                                        snackbarState.show("Adasf")
                                    }
                                }) { Text("A") }
                                UiSpacer(16.dp)
                                FloatingActionButton(onClick = {}) { Text("B") }
                            }
                        }
                    }
                }
            }
        }
    )

}

@Composable
private fun TopRow() {
    Row {
        ChooseVaultButton(
            vaultName = "Main Vault",
            isFastVault = false,
        )
        UiSpacer(
            weight = 1f
        )

        VsCircleButton(
            onClick = {},
            icon = R.drawable.gear,
            size = VsCircleButtonSize.Small,
            type = VsCircleButtonType.Secondary
        )

        UiSpacer(
            size = 8.dp
        )

        VsCircleButton(
            onClick = {},
            icon = R.drawable.gear,
            size = VsCircleButtonSize.Small,
            type = VsCircleButtonType.Secondary
        )
    }
}

