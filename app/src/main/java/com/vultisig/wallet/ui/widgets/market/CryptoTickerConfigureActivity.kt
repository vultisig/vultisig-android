package com.vultisig.wallet.ui.widgets.market

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.MarketWidgetAssetIdentity
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.inputs.VsSearchTextField
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.loading.V2Loading
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher-invoked picker for the Crypto Ticker widget's asset. Runs outside the main navigation
 * graph on purpose: the launcher opens it directly with the widget id, and it must not require an
 * unlocked vault.
 */
@AndroidEntryPoint
internal class CryptoTickerConfigureActivity : AppCompatActivity() {

    private val viewModel: CryptoTickerConfigureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancelled until an asset is stored; backing out must not leave a half-configured widget.
        setResult(RESULT_CANCELED)
        val appWidgetId =
            intent
                ?.extras
                ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val systemBarStyle =
            SystemBarStyle.auto(
                V2.colors.backgrounds.primary.toArgb(),
                V2.colors.backgrounds.primary.toArgb(),
            ) {
                true
            }
        enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = systemBarStyle)

        viewModel.loadCurrentSelection(appWidgetId)

        setContent {
            OnBoardingComposeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val saved by viewModel.saved.collectAsStateWithLifecycle()

                LaunchedEffect(saved) {
                    if (saved == appWidgetId) {
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    }
                }

                // Same inset handling as MainActivityContent: edge-to-edge window, content
                // kept clear of the system bars.
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Theme.v2.colors.backgrounds.primary)
                            .safeDrawingPadding()
                ) {
                    CryptoTickerConfigureScreen(
                        state = state,
                        searchFieldState = viewModel.searchFieldState,
                        onBackClick = ::finish,
                        onAssetClick = { viewModel.select(appWidgetId, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CryptoTickerConfigureScreen(
    state: CryptoTickerConfigureUiModel,
    searchFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onAssetClick: (MarketWidgetAssetIdentity) -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.market_widget_configure_title),
        onBackClick = onBackClick,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VsSearchTextField(fieldState = searchFieldState)

            val showingResults = state.query.isNotEmpty()
            val assets = if (showingResults) state.results else state.suggestions

            Text(
                text =
                    stringResource(
                        if (showingResults) R.string.market_widget_configure_results
                        else R.string.market_widget_configure_suggestions
                    ),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            UiSpacer(size = 8.dp)

            when {
                state.isSearching || state.isSaving -> CenteredMessage { V2Loading() }
                state.isSearchFailed ->
                    CenteredMessage {
                        Text(
                            text = stringResource(R.string.market_widget_configure_search_failed),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                        )
                    }
                showingResults && assets.isEmpty() ->
                    CenteredMessage {
                        Text(
                            text = stringResource(R.string.market_widget_configure_no_results),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.secondary,
                        )
                    }
                else ->
                    V2Container(type = ContainerType.SECONDARY) {
                        LazyColumn {
                            itemsIndexed(assets, key = { _, asset -> asset.id }) { index, asset ->
                                SettingItem(
                                    item =
                                        SettingsItemUiModel(
                                            title = asset.symbol.asUiText(),
                                            subTitle = asset.name.asUiText(),
                                            trailingIcon =
                                                if (asset.id == state.selectedId) R.drawable.check_2
                                                else null,
                                        ),
                                    onClick = { onAssetClick(asset) },
                                    isLastItem = index == assets.lastIndex,
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
