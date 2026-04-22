package com.vultisig.wallet.app.activity

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.models.SendDeeplinkData
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetKeysignTransactionSummaryUseCase
import com.vultisig.wallet.data.usecases.HandleTonConnectUriUseCase
import com.vultisig.wallet.data.usecases.InitializeThorChainNetworkIdUseCase
import com.vultisig.wallet.data.usecases.KeysignTransactionSummary
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigateAction
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal data class ForegroundNotificationState(
    val qrCodeData: String,
    val vaultName: String = "",
    val transactionSummary: String = "",
)

@HiltViewModel
internal class MainViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val snackbarFlow: SnackbarFlow,
    private val vaultRepository: VaultRepository,
    private val appUpdateManager: AppUpdateManager,
    private val initializeThorChainNetworkId: InitializeThorChainNetworkIdUseCase,
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase,
    private val handleTonConnectUri: HandleTonConnectUriUseCase,
    private val getKeysignTransactionSummary: GetKeysignTransactionSummaryUseCase,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    networkUtils: NetworkUtils,
) : ViewModel() {

    private val _navigationReady = CompletableDeferred<Unit>()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _startUpdateEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val startUpdateEvent = _startUpdateEvent.asSharedFlow()

    private val _startDestination = MutableStateFlow<Any>(Route.Home())
    val startDestination: StateFlow<Any> = _startDestination.asStateFlow()

    private val _foregroundNotification = MutableStateFlow<ForegroundNotificationState?>(null)
    val foregroundNotification: StateFlow<ForegroundNotificationState?> =
        _foregroundNotification.asStateFlow()

    val destination: Flow<NavigateAction<Destination>> = navigator.destination

    val route: Flow<NavigateAction<Any>> = navigator.route

    val snakeBarHostState =
        VSSnackbarState(duration = 1.seconds, coroutineScope = CoroutineScope(Dispatchers.Default))

    init {
        viewModelScope.safeLaunch {
            _startDestination.value = resolveStartDestination()
            _isLoading.value = false

            snackbarFlow.collectMessage { (message, type) -> snakeBarHostState.show(message, type) }
        }

        viewModelScope.safeLaunch { initializeThorChainNetworkId() }

        networkUtils
            .observeConnectivityAsFlow()
            .map { !it } // offline = not online
            .distinctUntilChanged()
            .onEach { _isOffline.value = it }
            .catch { Timber.w(it, "Connectivity flow failed") }
            .launchIn(viewModelScope)
    }

    fun onNavigationReady() {
        _navigationReady.complete(Unit)
    }

    private var foregroundPushJob: kotlinx.coroutines.Job? = null

    fun onForegroundPushReceived(qrCodeData: String) {
        foregroundPushJob?.cancel()
        foregroundPushJob =
            viewModelScope.safeLaunch {
                val pubKeyEcdsa = DeepLinkHelper(qrCodeData).getParameter("vault")
                val vault = pubKeyEcdsa?.let { vaultRepository.getByEcdsa(it) }
                val transactionSummary =
                    when (val summary = getKeysignTransactionSummary(qrCodeData)) {
                        is KeysignTransactionSummary.Swap ->
                            context.getString(
                                R.string.notification_banner_swap_summary,
                                mapTokenValueToStringWithUnit(summary.srcTokenValue),
                                summary.dstTicker,
                            )
                        is KeysignTransactionSummary.Send ->
                            context.getString(
                                R.string.notification_banner_send_summary,
                                mapTokenValueToStringWithUnit(summary.tokenValue),
                            )
                        null -> ""
                    }
                _foregroundNotification.value =
                    ForegroundNotificationState(
                        qrCodeData = qrCodeData,
                        vaultName = vault?.name ?: "",
                        transactionSummary = transactionSummary,
                    )
            }
    }

    fun onForegroundBannerTapped() {
        val qrCodeData = _foregroundNotification.value?.qrCodeData ?: return
        _foregroundNotification.value = null
        onPushNotificationReceived(qrCodeData)
    }

    fun onPushNotificationReceived(qrCodeData: String) {
        viewModelScope.safeLaunch {
            _navigationReady.await()
            val pubKeyEcdsa = DeepLinkHelper(qrCodeData).getParameter("vault")
            val vault = pubKeyEcdsa?.let { vaultRepository.getByEcdsa(it) }
            if (vault == null) {
                snackbarFlow.showMessage(
                    context.getString(R.string.push_notification_vault_not_found),
                    SnackbarType.Error,
                )
                return@safeLaunch
            }
            navigator.route(getDirectionByQrCodeUseCase(qrCodeData, vault.id))
        }
    }

    fun openUri(uri: Uri) {
        viewModelScope.safeLaunch {
            _navigationReady.await()
            val deepLinkHelper = DeepLinkHelper(uri)
            if (deepLinkHelper.isTonConnectUri()) {
                handleTonConnectUri(uri.toString())
            } else if (deepLinkHelper.isSendDeeplink()) {
                if (hasAnyVault()) {
                    navigator.route(
                        Route.VaultList(
                            openType =
                                Route.VaultList.OpenType.DeepLink(
                                    sendDeepLinkData =
                                        SendDeeplinkData(
                                            assetChain = deepLinkHelper.getAssetChain(),
                                            assetTicker = deepLinkHelper.getAssetTicker(),
                                            toAddress = deepLinkHelper.getToAddress(),
                                            amount = deepLinkHelper.getAmount(),
                                            memo = deepLinkHelper.getMemo(),
                                        )
                                )
                        )
                    )
                } else {
                    navigator.route(Route.ImportVault())
                }
            } else {
                navigator.route(Route.ImportVault(uri = uri.toString()))
            }
        }
    }

    fun checkUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                _startUpdateEvent.tryEmit(Unit)
        }
    }

    private suspend fun resolveStartDestination(): Any =
        if (hasAnyVault()) Route.Home() else Route.AddVault

    private suspend fun hasAnyVault(): Boolean =
        try {
            withTimeoutOrNull(SPLASH_VAULT_QUERY_TIMEOUT) { vaultRepository.hasVaults() }
                ?: false.also {
                    Timber.w(
                        "hasVaults() timed out after %ds; assuming no vaults",
                        SPLASH_VAULT_QUERY_TIMEOUT.inWholeSeconds,
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "hasVaults() failed; assuming no vaults")
            false
        }
}

private val SPLASH_VAULT_QUERY_TIMEOUT = 5.seconds
