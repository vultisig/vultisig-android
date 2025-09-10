package com.vultisig.wallet.ui.screens.v2.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.components.v2.scaffold.ExpandableTopbarScrollBehavior
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.scaffold.rememberExpandableTopbarScrollState
import kotlinx.coroutines.launch


@Composable
internal fun HomePage() {
    val lazyListState = rememberLazyListState()
    val isTopbarExpanded by rememberExpandableTopbarScrollState(
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
        isTopbarExpanded = isTopbarExpanded,
        topBarCollapsedContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondary)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Collapsed Mode Title",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        },
        topBarExpandedContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp)
            ) {
                Text("Top Left", Modifier.align(Alignment.TopStart), color = Color.White)
                Text("Top Right", Modifier.align(Alignment.TopEnd), color = Color.White)
                Text("Bottom Left", Modifier.align(Alignment.BottomStart), color = Color.White)
                Text("Bottom Right", Modifier.align(Alignment.BottomEnd), color = Color.White)
            }
        },


        bottomBarContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(onClick = {
                    isBottomSheetVisible = true
                }) { Text("A") }
                UiSpacer(16.dp)
                FloatingActionButton(onClick = {}) { Text("B") }
            }
        },
        content = {
            Box{

                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
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

