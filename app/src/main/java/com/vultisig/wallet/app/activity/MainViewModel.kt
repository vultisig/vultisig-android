package com.vultisig.wallet.app.activity

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.UpdateAvailability
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.data.usecases.InitializeThorChainNetworkIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigateAction
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    navigator: Navigator<Destination>,
    private val snackbarFlow: SnackbarFlow,
    private val vaultRepository: VaultRepository,
    private val discoverToken: DiscoverTokenUseCase,
    private val appUpdateManager: AppUpdateManager,
    private val initializeThorChainNetworkId: InitializeThorChainNetworkIdUseCase,
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _startDestination: MutableState<String> = mutableStateOf(Destination.Home().route)
    val startDestination: State<String> = _startDestination

    val destination: Flow<NavigateAction<Destination>> = navigator.destination

    val route: Flow<Any> = navigator.route

    val snakeBarHostState = SnackbarHostState()

    init {
        viewModelScope.launch {
            if (vaultRepository.hasVaults()) {
                _startDestination.value = Destination.Home().route
                _isLoading.value = false
            } else {
                val isUserPassedOnboarding = repository.readOnBoardingState()
                    .first()

                if (isUserPassedOnboarding) {
                    _startDestination.value = Destination.AddVault.route
                } else {
                    _startDestination.value = Destination.Welcome.route
                }

                _isLoading.value = false
            }

            snackbarFlow.collectMessage {
                snakeBarHostState.showSnackbar(it)
            }
        }

        viewModelScope.launch {
            initializeThorChainNetworkId()
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
