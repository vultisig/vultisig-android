package com.vultisig.wallet.ui.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.OnBoardPage
import com.vultisig.wallet.data.repositories.OnBoardRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


data class WelcomeState(
    val pages: List<OnBoardPage> = emptyList(),
)

internal sealed class UiEvent {
    data class ScrollToNextPage(val screen: Destination) : UiEvent()
}

@HiltViewModel
internal class WelcomeViewModel @Inject constructor(
    private val repository: OnBoardRepository,
    private val vaultsRepository: VaultRepository,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    var state by mutableStateOf(WelcomeState())
        private set
    private var _channel = Channel<UiEvent>()
    var channel = _channel.receiveAsFlow()

    init {
        state = WelcomeState(pages)
    }

    fun scrollToNextPage() {
        viewModelScope.launch(Dispatchers.IO) {
            val dest = if (vaultsRepository.hasVaults())
                Destination.Home()
            else Destination.AddVault

            _channel.send(UiEvent.ScrollToNextPage(dest))
        }
    }

    fun skip() {
        saveOnBoardingState()
    }

    private fun saveOnBoardingState() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveOnBoardingState(completed = true)

            val dest = if (vaultsRepository.hasVaults())
                Destination.Home()
            else Destination.AddVault

            navigator.navigate(dest)
        }
    }
}

private val pages = listOf(
    OnBoardPage(
        image = R.drawable.intro1,
        title = R.string.onboard_intro1_title,
        description = R.string.onboard_intro1_desc
    ), OnBoardPage(
        image = R.drawable.intro2,
        title = R.string.onboard_intro2_title,
        description = R.string.onboard_intro2_desc,
    ), OnBoardPage(
        image = R.drawable.intro3,
        title = R.string.onboard_intro3_title,
        description = R.string.onboard_intro3_desc,
    ), OnBoardPage(
        image = R.drawable.intro4,
        title = R.string.onboard_intro4_title,
        description = R.string.onboard_intro4_desc,
    )
)