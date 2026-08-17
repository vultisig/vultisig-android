package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.runtime.Immutable
import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal

/**
 * A fiat figure together with the currency it was priced in.
 *
 * A chain header adds up halves that separate view-models price, each reading the selected currency
 * on its own clock. Carrying the currency lets that sum refuse to happen while a mid-session switch
 * has only landed on one side, instead of adding euros to dollars.
 */
@Immutable data class DefiFiatTotal(val value: BigDecimal, val currency: AppCurrency)
