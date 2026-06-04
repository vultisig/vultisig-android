package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger

/**
 * Structured intent decoded from a TonConnect message body (BOC).
 *
 * Addresses are returned in raw `workchain:hex` form. The UI layer converts them to the
 * user-friendly bounceable form via WalletCore's `TONAddressConverter`, keeping this decoder free
 * of JNI so it stays unit testable on the JVM.
 */
sealed interface TonMessageBodyIntent {

    /** TEP-74 jetton transfer (`0x0f8a7ea5`). */
    data class JettonTransfer(
        val queryId: BigInteger,
        val amount: BigInteger,
        val destination: String,
        val responseDestination: String?,
        val forwardTonAmount: BigInteger,
    ) : TonMessageBodyIntent

    /** TEP-62 NFT transfer (`0x5fcc3d14`). */
    data class NftTransfer(
        val queryId: BigInteger,
        val newOwner: String,
        val responseDestination: String?,
        val forwardAmount: BigInteger,
    ) : TonMessageBodyIntent

    /** TEP-74 excess-gas return notification (`0xd53276db`). */
    data class Excesses(val queryId: BigInteger) : TonMessageBodyIntent
}
