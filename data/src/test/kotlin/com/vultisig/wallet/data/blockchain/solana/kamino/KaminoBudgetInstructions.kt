package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger

/**
 * The two ComputeBudget instructions [KaminoTransactionPreparer] injects, at the positions it puts
 * them: the unit limit first, the unit price second.
 *
 * Every valid Kamino transaction carries this pair, so the fixtures in these tests do too — the
 * validator refuses a transaction whose budget is missing, misplaced or not the one this app builds
 * for the vault and action in hand.
 */
internal fun computeBudgetInstructions(
    vault: KaminoVault,
    action: KaminoAction,
    price: BigInteger = KaminoComputeBudget.FALLBACK_UNIT_PRICE,
    limit: BigInteger = KaminoComputeBudget.unitLimitFor(vault, action),
): List<KaminoTxInstruction> =
    listOf(
        KaminoTxInstruction(KaminoComputeBudget.PROGRAM_ID, setUnitLimitData(limit)),
        KaminoTxInstruction(
            KaminoComputeBudget.PROGRAM_ID,
            KaminoComputeBudget.setUnitPriceData(price),
        ),
    )

/**
 * Borsh-encoded `SetComputeUnitLimit`: the discriminator byte followed by the limit as a
 * little-endian `u32`.
 *
 * Written here rather than in production code because nothing there encodes one — WalletCore's
 * `SolanaTransaction.setComputeUnitLimit` does it inside the JNI layer.
 */
internal fun setUnitLimitData(limit: BigInteger): ByteArray =
    byteArrayOf(KaminoComputeBudget.SET_UNIT_LIMIT_DISCRIMINATOR.toByte()) +
        ByteArray(Int.SIZE_BYTES) { byte -> limit.shiftRight(8 * byte).toInt().toByte() }
