package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable

/**
 * Stable holder for the [TextFieldState]s backing the deposit form's editable inputs.
 *
 * Grouping the field states in one object lets `DepositFormViewModel` hand the whole set to
 * strategy construction without re-declaring each field, while it keeps exposing the individual
 * states to Compose unchanged.
 */
@Stable
internal class DepositFieldStates {
    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()
    val nodeAddressFieldState = TextFieldState()
    val providerFieldState = TextFieldState()
    val operatorFeeFieldState = TextFieldState()
    val customMemoFieldState = TextFieldState()
    val basisPointsFieldState = TextFieldState()
    val lpUnitsFieldState = TextFieldState()
    val assetsFieldState = TextFieldState()
    val thorAddressFieldState = TextFieldState()
    val rewardsAmountFieldState = TextFieldState()
    val slippageFieldState = TextFieldState()
}
