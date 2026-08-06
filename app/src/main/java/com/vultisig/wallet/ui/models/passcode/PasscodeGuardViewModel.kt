package com.vultisig.wallet.ui.models.passcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Exposes the lock state that decides whether the app content or the lock screen is shown. */
@HiltViewModel
internal class PasscodeGuardViewModel
@Inject
constructor(private val passcodeRepository: PasscodeRepository) : ViewModel() {

    val state: StateFlow<PasscodeState> = passcodeRepository.state

    init {
        // Reads the persisted credentials off the main thread; until it completes the state stays
        // Unknown and the guard shows nothing rather than flashing unlocked content.
        viewModelScope.launch { passcodeRepository.initialize() }
    }
}
