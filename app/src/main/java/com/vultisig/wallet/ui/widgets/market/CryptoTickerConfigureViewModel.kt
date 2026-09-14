package com.vultisig.wallet.ui.widgets.market

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.MarketWidgetAssetIdentity
import com.vultisig.wallet.data.models.MarketWidgetQuery
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.MarketWidgetRepository
import com.vultisig.wallet.data.utils.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@Immutable
internal data class CryptoTickerConfigureUiModel(
    val suggestions: List<MarketWidgetAssetIdentity> = CryptoTickerConfigureViewModel.SUGGESTIONS,
    val results: List<MarketWidgetAssetIdentity> = emptyList(),
    val selectedId: String = CryptoTickerWidget.DEFAULT_ASSET.id,
    val isSearching: Boolean = false,
    val isSearchFailed: Boolean = false,
    val isSaving: Boolean = false,
    /** The query the current [results] answer; empty means the suggestions are showing. */
    val query: String = "",
)

@HiltViewModel
internal class CryptoTickerConfigureViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val repository: MarketWidgetRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ViewModel() {

    val searchFieldState = TextFieldState()

    private val _state = MutableStateFlow(CryptoTickerConfigureUiModel())
    val state: StateFlow<CryptoTickerConfigureUiModel> = _state.asStateFlow()

    private val _saved = MutableStateFlow<Int?>(null)

    /** The app widget id whose asset was just stored, so the activity can finish with a result. */
    val saved: StateFlow<Int?> = _saved.asStateFlow()

    init {
        observeSearch()
    }

    /** Reflect the asset a re-configured widget already shows, so the tick lands on it. */
    fun loadCurrentSelection(appWidgetId: Int) {
        // A widget that can't be resolved simply keeps the default tick; nothing to surface.
        viewModelScope.safeLaunch(onError = { Timber.d(it, "No stored widget selection") }) {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            _state.update { it.copy(selectedId = CryptoTickerWidget.selectedAssetId(prefs)) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.safeLaunch {
            snapshotFlow { searchFieldState.text.toString().trim() }
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query -> search(query) }
        }
    }

    private suspend fun search(query: String) {
        if (query.isEmpty()) {
            _state.update {
                it.copy(
                    query = "",
                    results = emptyList(),
                    isSearching = false,
                    isSearchFailed = false,
                )
            }
            return
        }
        _state.update { it.copy(query = query, isSearching = true, isSearchFailed = false) }
        val results =
            try {
                repository.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Widget asset search failed")
                null
            }
        // The field may have moved on while the request was in flight; only publish an answer
        // for the query the user is still looking at.
        if (searchFieldState.text.toString().trim() != query) return
        _state.update {
            it.copy(
                results = results.orEmpty(),
                isSearching = false,
                isSearchFailed = results == null,
            )
        }
    }

    fun select(appWidgetId: Int, asset: MarketWidgetAssetIdentity) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, selectedId = asset.id) }
        viewModelScope.safeLaunch(
            onError = { e ->
                // The selection was not persisted; release the guard so the user can retry.
                Timber.e(e, "Could not store the widget asset selection")
                _state.update { it.copy(isSaving = false) }
            }
        ) {
            val manager = GlanceAppWidgetManager(context)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[CryptoTickerWidget.KEY_ASSET_ID] = asset.id
                    this[CryptoTickerWidget.KEY_ASSET_SYMBOL] = asset.symbol
                    this[CryptoTickerWidget.KEY_ASSET_NAME] = asset.name
                }
            }

            // Everything from here is best effort: the choice is stored, so the widget lands on
            // the home screen correctly even if the first render or prefetch doesn't happen now.
            // If this doesn't make it in time the widget renders its loading state and the
            // refresh worker fills it in.
            withTimeoutOrNull(PREFETCH_TIMEOUT_MS) {
                bestEffort("Widget prefetch failed; deferring to the refresh worker") {
                    val currency = appCurrencyRepository.currency.first().ticker
                    repository.refresh(MarketWidgetQuery.Ids(listOf(asset.id)), currency)
                }
            }
            bestEffort("Widget render after configuration failed") {
                CryptoTickerWidget().update(context, glanceId)
            }
            bestEffort("Could not schedule the widget refresh") {
                MarketWidgetRefreshWorker.schedulePeriodic(context)
            }
            _saved.value = appWidgetId
        }
    }

    private inline fun bestEffort(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, message)
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val PREFETCH_TIMEOUT_MS = 8_000L

        val SUGGESTIONS =
            listOf(
                CryptoTickerWidget.DEFAULT_ASSET,
                MarketWidgetAssetIdentity("ethereum", "ETH", "Ethereum"),
                MarketWidgetAssetIdentity("tether", "USDT", "Tether"),
                MarketWidgetAssetIdentity("binancecoin", "BNB", "BNB"),
                MarketWidgetAssetIdentity("solana", "SOL", "Solana"),
                MarketWidgetAssetIdentity("usd-coin", "USDC", "USDC"),
                MarketWidgetAssetIdentity("ripple", "XRP", "XRP"),
                MarketWidgetAssetIdentity("dogecoin", "DOGE", "Dogecoin"),
            )
    }
}
