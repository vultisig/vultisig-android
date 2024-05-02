package com.voltix.wallet.app.activity

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltix.wallet.data.on_board.db.VaultDB
import com.voltix.wallet.on_board.OnBoardRepository
import com.voltix.wallet.presenter.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: OnBoardRepository,
) : ViewModel() {

    private val _isLoading: MutableState<Boolean> = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _startDestination: MutableState<String> = mutableStateOf(Screen.Welcome.route)
    val startDestination: State<String> = _startDestination

    fun setData(context: Context){
        viewModelScope.launch {
            val vaultDB = VaultDB(context)
            val listVaults = vaultDB.selectAll()
            if(listVaults.isNotEmpty()){
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