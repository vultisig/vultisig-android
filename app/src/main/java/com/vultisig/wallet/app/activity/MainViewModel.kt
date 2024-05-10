package com.vultisig.wallet.app.activity

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject


@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val navigator: Navigator<Destination>,
    private val vaultDB: VaultDB,
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _startDestination: MutableState<String> = mutableStateOf(Screen.Welcome.route)
    val startDestination: State<String> = _startDestination

    val destination = navigator.destination

    fun setData(context: Context) {
        viewModelScope.launch {
            Logger.getLogger(OkHttpClient::class.java.name).setLevel(Level.FINE)
            val listVaults = vaultDB.selectAll()
            if (listVaults.isNotEmpty()) {
                _startDestination.value = Screen.Home.route
                return@launch
            }
            repository.readOnBoardingState().collect { completed ->
                if (completed) {
                    _startDestination.value = Screen.CreateNewVault.route
                } else {
                    _startDestination.value = Screen.Welcome.route
                }
            }
            _isLoading.value = false
        }
    }

}