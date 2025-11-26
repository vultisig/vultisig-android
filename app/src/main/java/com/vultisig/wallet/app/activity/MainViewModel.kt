package com.vultisig.wallet.app.activity

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.models.SendDeeplinkData
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.InitializeThorChainNetworkIdUseCase
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigateAction
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils.observeConnectivityAsFlow
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val snackbarFlow: SnackbarFlow,
    private val vaultRepository: VaultRepository,
    private val appUpdateManager: AppUpdateManager,
    private val initializeThorChainNetworkId: InitializeThorChainNetworkIdUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading
    private val _isOffline: MutableState<Boolean> = mutableStateOf(false)
    val isOffline: State<Boolean> = _isOffline

    private val _startDestination: MutableState<Any> = mutableStateOf(Route.Home())
    val startDestination: State<Any> = _startDestination

    val destination: Flow<NavigateAction<Destination>> = navigator.destination

    val route: Flow<NavigateAction<Any>> = navigator.route

    val snakeBarHostState = VSSnackbarState(
        duration = 1.seconds,
        coroutineScope = CoroutineScope(Dispatchers.Default)
    )

    init {
        viewModelScope.launch {

            if (vaultRepository.hasVaults())
                _startDestination.value = Route.Home()
            else
                _startDestination.value = Route.AddVault

            _isLoading.value = false

            snackbarFlow.collectMessage {
                snakeBarHostState.show(it)
            }
        }

        viewModelScope.launch {
            initializeThorChainNetworkId()
        }

        context
            .observeConnectivityAsFlow()
            .map { !it } // offline = not online
            .distinctUntilChanged()
            .onEach { _isOffline.value = it }
            .catch {
                Timber.w(
                    it,
                    "Connectivity flow failed"
                )
            }
            .launchIn(viewModelScope)
    }

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            delay(1.seconds)
            val deepLinkHelper = DeepLinkHelper(uri)
            if (deepLinkHelper.isSendDeeplink()) {
                if(vaultRepository.hasVaults()) {
                    navigator.route(
                        Route.VaultList(
                            openType = Route.VaultList.OpenType.DeepLink(
                                sendDeepLinkData = SendDeeplinkData(
                                    assetChain = deepLinkHelper.getAssetChain(),
                                    assetTicker = deepLinkHelper.getAssetTicker(),
                                    toAddress = deepLinkHelper.getToAddress(),
                                    amount = deepLinkHelper.getAmount(),
                                    memo = deepLinkHelper.getMemo()
                                )
                            )
                        )
                    )
                } else {
                    navigator.route(
                        Route.ImportVault()
                    )
                }
            } else {
                navigator.route(
                    Route.ImportVault(
                        uri = uri.toString()
                    )
                )
            }
        }
    }

    fun checkUpdates(onUpdateAvailable: (AppUpdateInfo) -> Unit) {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                when (appUpdateInfo.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        onUpdateAvailable(appUpdateInfo)
                    }

                    else -> Unit
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
