package com.vultisig.wallet.ui.models.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@Immutable
data class LockTimeUiState(
    val selectedLockTime: Duration? = null,
    val lockTimes: List<Duration?> = LOCK_TIMES
)


@HiltViewModel
internal class LockTimeViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val uiState = MutableStateFlow(
        LockTimeUiState()
    )

    fun onLockTimeSelected(lockTime: Duration?) {
        uiState.value = uiState.value.copy(
            selectedLockTime = lockTime
        )
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }

}

private val LOCK_TIMES = listOf(
    15.seconds,
    30.seconds,
    1.minutes,
    5.minutes,
    10.minutes,
    30.minutes,
    null // never
)