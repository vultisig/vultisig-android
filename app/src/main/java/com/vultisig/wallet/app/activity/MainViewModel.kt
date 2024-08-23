package com.vultisig.wallet.app.activity

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigateAction
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _startDestination: MutableState<String> = mutableStateOf(Screen.Home.route)
    val startDestination: State<String> = _startDestination

    val destination: Flow<NavigateAction<Destination>> = navigator.destination

    val snakeBarHostState = SnackbarHostState()

    init {
        viewModelScope.launch {
            if (vaultRepository.hasVaults()) {
                _startDestination.value = Screen.Home.route
                _isLoading.value = false
            } else {
                val isUserPassedOnboarding = repository.readOnBoardingState()
                    .first()

                if (isUserPassedOnboarding) {
                    _startDestination.value = Screen.CreateNewVault.route
                } else {
                    _startDestination.value = Screen.Welcome.route
                }

                _isLoading.value = false
            }

            snackbarFlow.collectMessage {
                snakeBarHostState.showSnackbar(it)
            }
        }
    }

    fun checkUpdates(savedInstanceState: Bundle?){
        viewModelScope.launch {
            try {
                if (savedInstanceState != null) {
                    return@launch
                } else {
                    appUpdateManager.appUpdateInfo.addOnSuccessListener{
                            appUpdateInfo ->
                        when (appUpdateInfo.updateAvailability()) {
                            UpdateAvailability.UPDATE_AVAILABLE -> {
                                appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    context as Activity,
                                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                                        .setAllowAssetPackDeletion(true)
                                        .build(), 0
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}
