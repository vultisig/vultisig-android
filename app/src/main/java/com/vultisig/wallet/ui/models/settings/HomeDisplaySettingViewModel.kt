package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.HomeBalanceDisplay
import com.vultisig.wallet.data.repositories.HomeDisplayRepository
import com.vultisig.wallet.data.utils.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class HomeDisplaySettingViewModel
@Inject
constructor(private val homeDisplayRepository: HomeDisplayRepository) : ViewModel() {

    /** The chosen display, or null until the stored value has been read. */
    val selected: StateFlow<HomeBalanceDisplay?> =
        homeDisplayRepository.balanceDisplay.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            null,
        )

    fun select(display: HomeBalanceDisplay) {
        viewModelScope.safeLaunch { homeDisplayRepository.setBalanceDisplay(display) }
    }
}
