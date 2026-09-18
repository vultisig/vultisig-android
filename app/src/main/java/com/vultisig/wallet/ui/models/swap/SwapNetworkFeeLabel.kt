package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.isOpStackL2

/**
 * Whether the network fee shown for a swap out of this chain is a ceiling rather than the exact
 * cost. EVM gas is quoted as maxFeePerGas × gas limit — the bond the node requires, the most the
 * transaction can cost — so its row is labelled as a maximum, in line with the "Max. Total Fee"
 * line below it. Every other chain quotes exactly what it charges and keeps the plain label.
 *
 * OP-stack L2s are the exception within EVM: [evmSwapDisplayGasLimit] keeps their row at the flat
 * [com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.DEFAULT_SWAP_LIMIT] while the
 * signer bonds `maxOf(routeGas, limit)`, and the L1 data fee op-geth bills on top is an oracle
 * estimate the co-signer never sees. That figure is an estimate, not a bound, so it keeps the plain
 * label too.
 */
internal val Chain.hasSwapNetworkFeeCeiling: Boolean
    get() = standard == TokenStandard.EVM && !isOpStackL2
