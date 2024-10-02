package com.vultisig.wallet.ui.utils

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow

internal fun TextFieldState.textAsFlow() = snapshotFlow { text }