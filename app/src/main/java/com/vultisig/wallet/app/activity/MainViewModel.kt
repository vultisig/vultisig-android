package com.vultisig.wallet.app.activity

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SendDeeplinkData
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetKeysignTransactionSummaryUseCase
import com.vultisig.wallet.data.usecases.InitializeThorChainNetworkIdUseCase
import com.vultisig.wallet.data.usecases.KeysignTransactionSummary
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigateAction
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal data class ForegroundNotificationState(
    val qrCodeData: String,
    val vaultName: String = "",
    val transactionSummary: UiText = UiText.Empty,
)

@HiltViewModel
internal class MainViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val snackbarFlow: SnackbarFlow,
    private val vaultRepository: VaultRepository,
    private val appUpdateManager: AppUpdateManager,
    private val initializeThorChainNetworkId: InitializeThorChainNetworkIdUseCase,
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase,
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

    val snackbarState: VSSnackbarState = VSSnackbarState(1.seconds, viewModelScope)

    init {
        viewModelScope.safeLaunch {
            _startDestination.value = resolveStartDestination()
            _isLoading.value = false
        }

        viewModelScope.safeLaunch {
            // Re-trigger after THORChain first appears in any vault, so adding a
            // THORChain coin (or importing a THORChain vault) mid-session still
            // initializes the live network id instead of relying on the default.
            // Uses the lightweight DAO `EXISTS` flow so the cold-start path doesn't
            // hydrate the full vault graph for non-THORChain users.
            vaultRepository
                .observeHasAnyCoinOnChain(Chain.ThorChain)
                .distinctUntilChanged()
                .filter { it }
                .first()
            initializeThorChainNetworkId()
        }

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
                val transactionSummary: UiText =
                    when (val summary = getKeysignTransactionSummary(qrCodeData)) {
                        is KeysignTransactionSummary.Swap ->
                            UiText.FormattedText(
                                R.string.notification_banner_swap_summary,
                                listOf(
                                    mapTokenValueToStringWithUnit(summary.srcTokenValue),
                                    summary.dstTicker,
                                ),
                            )
                        is KeysignTransactionSummary.Send ->
                            UiText.FormattedText(
                                R.string.notification_banner_send_summary,
                                listOf(mapTokenValueToStringWithUnit(summary.tokenValue)),
                            )
                        is KeysignTransactionSummary.DappTransaction ->
                            summary.summary?.let { UiText.DynamicString(it) }
                                ?: UiText.StringResource(R.string.ripple_dapp_transaction)
                        null -> UiText.Empty
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
        // Do NOT clear the banner here. Clearing is bound to banner visibility, so an eager
        // clear hides the banner before navigation lands — and if navigation is dropped (the
        // user is inside a nested keysign/send flow), the user is left with no banner and no
        // destination. The route-change observer in MainActivityContent clears the banner once
        // Route.Keysign.Join / Keygen.Join is actually reached. Until then the banner stays so
        // the signing request remains actionable; the user can also swipe it away to dismiss.
        onPushNotificationReceived(qrCodeData)
    }

    fun clearForegroundNotification() {
        foregroundPushJob?.cancel()
        // Cancel any in-flight navigation a banner tap may have launched, so swiping the
        // banner away (or any other dismissal) does not still land the user on the Join
        // screen they tried to dismiss while the lookup was still suspended.
        navigationJob?.cancel()
        _foregroundNotification.value = null
    }

    private var navigationJob: kotlinx.coroutines.Job? = null

    fun onPushNotificationReceived(qrCodeData: String) {
        navigationJob?.cancel()
        navigationJob =
            viewModelScope.safeLaunch {
                _navigationReady.await()
                val pubKeyEcdsa = DeepLinkHelper(qrCodeData).getParameter("vault")
                val vault = pubKeyEcdsa?.let { vaultRepository.getByEcdsa(it) }
                if (vault == null) {
                    snackbarFlow.showMessage(
                        UiText.StringResource(R.string.push_notification_vault_not_found),
                        SnackbarType.Error,
                    )
                    // No navigation happens on this branch, so the route-change observer will
                    // never clear the banner — clear it here so it doesn't linger forever.
                    _foregroundNotification.value = null
                    return@safeLaunch
                }
                val direction = getDirectionByQrCodeUseCase(qrCodeData, vault.id)
                // A join must always land on a FRESH Keysign.Join / Keygen.Join entry.
                // NavController.buildOptions sets launchSingleTop = true on every navigation, so
                // without popUpTo the navigator reuses an existing join entry and its Hilt-scoped
                // ViewModel — whose QR was read once at init via the route args. If the joiner is
                // already sitting on a finished/polling Keysign.Join entry, the second request
                // would silently reuse that dead ViewModel and never start the new join (#4623).
                // Popping the prior join entry inclusive forces a new entry (and tears down the
                // old polling ViewModel). Mirrors JoinKeygenViewModel and the cosmos staking VMs.
                when (direction) {
                    is Route.Keysign.Join ->
                        navigator.route(
                            direction,
                            NavigationOptions(
                                popUpToRoute = Route.Keysign.Join::class,
                                inclusive = true,
                            ),
                        )

                    is Route.Keygen.Join ->
                        navigator.route(
                            direction,
                            NavigationOptions(
                                popUpToRoute = Route.Keygen.Join::class,
                                inclusive = true,
                            ),
                        )

                    else -> {
                        navigator.route(direction)
                        // Join routes are cleared by the route-change observer in
                        // MainActivityContent once the destination is actually reached. That
                        // deferral protects the banner when the user is inside a nested flow and
                        // navigation may not land immediately. Destinations that never reach that
                        // observer (Send, ScanError) are cleared here so the banner doesn't stay
                        // stuck after a successful dispatch.
                        _foregroundNotification.value = null
                    }
                }
            }
    }

    fun openUri(uri: Uri) {
        viewModelScope.safeLaunch {
            _navigationReady.await()
            val deepLinkHelper = DeepLinkHelper(uri)
            if (deepLinkHelper.isSendDeeplink()) {
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
