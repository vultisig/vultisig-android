package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard

/**
 * Whether the network fee shown for a swap out of this chain is a ceiling rather than the exact
 * cost. EVM gas is quoted as maxFeePerGas × gas limit — the bond the node requires, the most the
 * transaction can cost — so its row is labelled as a maximum, in line with the "Max. Total Fee"
 * line below it. Every other chain quotes exactly what it charges and keeps the plain label.
 */
internal val Chain.hasSwapNetworkFeeCeiling: Boolean
    get() = standard == TokenStandard.EVM
