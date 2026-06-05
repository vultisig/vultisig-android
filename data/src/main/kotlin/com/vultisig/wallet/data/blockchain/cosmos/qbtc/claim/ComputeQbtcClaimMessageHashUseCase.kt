@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import javax.inject.Inject

/**
 * Computes the 32-byte QBTC claim message hash (lowercase hex) that the vault's Bitcoin key signs.
 * Exposed as a use case so the co-signing peer — which lives in the app module and can't see the
 * internal hashing — can recompute the hash from its own vault instead of trusting anything carried
 * in the pairing QR.
 */
interface ComputeQbtcClaimMessageHashUseCase {
    /**
     * @throws IllegalArgumentException for unsupported (P2TR/testnet/malformed) addresses or keys.
     */
    operator fun invoke(
        btcAddress: String,
        compressedPubkeyHex: String,
        qbtcAddress: String,
    ): String
}

internal class ComputeQbtcClaimMessageHashUseCaseImpl @Inject constructor() :
    ComputeQbtcClaimMessageHashUseCase {

    override fun invoke(
        btcAddress: String,
        compressedPubkeyHex: String,
        qbtcAddress: String,
    ): String =
        QbtcClaimHashes.computeAll(
                btcAddress = btcAddress,
                compressedPubkey = compressedPubkeyHex.hexToByteArray(),
                qbtcAddress = qbtcAddress,
                chainId = QbtcClaimConfig.CHAIN_ID,
            )
            .messageHash
            .toHexString()
}
