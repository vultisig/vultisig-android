package com.vultisig.wallet.app.activity

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigateAction
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val appLocaleRepository: AppLocaleRepository,

    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _startDestination: MutableState<String> = mutableStateOf(Screen.Home.route)
    val startDestination: State<String> = _startDestination

    val destination: Flow<NavigateAction<Destination>> = navigator.destination

    init {
        viewModelScope.launch {
            initStartDestination()
            setAppLanguage()
        }
    }

    private suspend fun initStartDestination() {
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
    }

    private suspend fun setAppLanguage() {
            val locale = appLocaleRepository.local.first().mainName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(locale)
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(locale)
                )
            }

    }

}
