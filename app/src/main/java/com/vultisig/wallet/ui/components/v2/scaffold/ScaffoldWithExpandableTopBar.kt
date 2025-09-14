package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.theme.Theme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScaffoldWithExpandableTopBar(
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    snackbarState: VSSnackbarState = rememberVsSnackbarState(),
    backgroundColor : Color = Theme.colors.backgrounds.primary,
    topBarExpandedContent: @Composable BoxScope.() -> Unit,
    topBarCollapsedContent: @Composable BoxScope.() -> Unit,
    bottomBarContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {

    PullToRefreshBox(
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
    ) {
        Scaffold(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = {
                VsSnackBar(snackbarState = snackbarState)
            },
            topBar = {
                VsExpandableTopBar(
                    expandedContent = topBarExpandedContent,
                    collapsedContent = topBarCollapsedContent,
                    scrollBehavior = scrollBehavior,
                    backgroundColor = backgroundColor,
                )
            }
        ) { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                Box(
                    content = content,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    content = bottomBarContent,
                )
            }
        }
    }


}


