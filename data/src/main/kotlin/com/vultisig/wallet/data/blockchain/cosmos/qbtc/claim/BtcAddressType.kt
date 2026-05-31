package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

/** ZK circuit used for the QBTC claim proof. */
internal enum class QbtcClaimCircuit {
    ECDSA,
    SCHNORR,
}

/**
 * Bitcoin address type, detected from the address string. Drives which ZK circuit the claim uses.
 * Only the ECDSA circuit is usable end-to-end today; the chain has not defined a Schnorr/Taproot
 * tag yet (btcq-org/qbtc#148).
 *
 * Mirrors `vultisig-sdk/.../claim/detectBtcAddressType.ts`.
 */
internal enum class BtcAddressType(val circuit: QbtcClaimCircuit) {
    P2PKH(QbtcClaimCircuit.ECDSA),
    P2WPKH(QbtcClaimCircuit.ECDSA),
    P2SH_P2WPKH(QbtcClaimCircuit.ECDSA),
    P2WSH(QbtcClaimCircuit.ECDSA),
    P2TR(QbtcClaimCircuit.SCHNORR);

    companion object {
        /**
         * Detects the address type from its prefix and length. Throws
         * [UnsupportedBtcAddressException] for formats the claim can't handle (testnet, unknown, or
         * malformed lengths).
         */
        fun detect(address: String): BtcAddressType =
            when {
                address.startsWith("tb1") ->
                    throw UnsupportedBtcAddressException(
                        "Testnet Bitcoin addresses are not supported: $address"
                    )
                address.startsWith("1") -> P2PKH
                address.startsWith("3") -> P2SH_P2WPKH
                address.startsWith("bc1p") ->
                    if (address.length == 62) P2TR
                    else
                        throw UnsupportedBtcAddressException(
                            "Unsupported Bitcoin address format: $address"
                        )
                address.startsWith("bc1q") ->
                    when (address.length) {
                        42 -> P2WPKH
                        62 -> P2WSH
                        else ->
                            throw UnsupportedBtcAddressException(
                                "Unsupported Bitcoin address format: $address"
                            )
                    }
                else ->
                    throw UnsupportedBtcAddressException(
                        "Unsupported Bitcoin address format: $address"
                    )
            }
    }
}

internal class UnsupportedBtcAddressException(message: String) : IllegalArgumentException(message)
