package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

/**
 * Authoritative balance-change information derived from a Blockaid simulation.
 *
 * Two shapes correspond to the calldata patterns the dApp hero promotes:
 * - [Transfer] — single asset leaving the user's wallet (e.g. ERC20 transfer)
 * - [Swap] — paired out/in assets (e.g. Uniswap router calls)
 *
 * The model intentionally mirrors the iOS `BlockaidSimulationInfo` and the vultisig-windows
 * extension parser so all three platforms surface the same data to the user.
 */
sealed interface BlockaidSimulationInfo {

    val fromCoin: BlockaidSimulationCoin
    val fromAmount: BigInteger

    data class Transfer(
        override val fromCoin: BlockaidSimulationCoin,
        override val fromAmount: BigInteger,
    ) : BlockaidSimulationInfo

    data class Swap(
        override val fromCoin: BlockaidSimulationCoin,
        val toCoin: BlockaidSimulationCoin,
        override val fromAmount: BigInteger,
        val toAmount: BigInteger,
    ) : BlockaidSimulationInfo
}

/**
 * Token metadata for a simulated balance change.
 *
 * Address is nullable because native assets (ETH, native SOL via wrapped-SOL sentinel) do not
 * always carry a contract address in Blockaid's response.
 */
data class BlockaidSimulationCoin(
    val chain: Chain,
    val address: String?,
    val ticker: String,
    val logo: String,
    val decimals: Int,
)

/**
 * Combined output of a single Blockaid scan.
 *
 * Pairs the parsed [simulation] (drives the dApp hero) with the existing [scannerResult] (drives
 * the security badge). Allows verify → sign → done to resolve both pieces of state from one cached
 * lookup.
 */
data class BlockaidKeysignScanResult(
    val simulation: BlockaidSimulationInfo?,
    val scannerResult: com.vultisig.wallet.data.securityscanner.SecurityScannerResult?,
) {
    companion object {
        val EMPTY = BlockaidKeysignScanResult(simulation = null, scannerResult = null)
    }
}
