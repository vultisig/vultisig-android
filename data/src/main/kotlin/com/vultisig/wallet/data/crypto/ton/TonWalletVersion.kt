package com.vultisig.wallet.data.crypto.ton

/**
 * The wallet contracts a Vultisig TON account can live in. The same Ed25519 key yields a different
 * address under each, so they are different accounts. Mirrors SDK `tonWalletVersions`.
 *
 * [maxMessagesPerRequest] is how many outgoing messages one external request may carry: V4 is
 * limited by the wallet code, W5 by its 255-entry action list.
 */
enum class TonWalletVersion(val maxMessagesPerRequest: Int) {
    V4R2(4),
    V5R1(255);

    companion object {

        /**
         * W5R1 mainnet wallet id: the network's global id (-239) XOR'd with the client context `1 |
         * workchain 0 | version 0 | subwallet 0`. WalletCore hardcodes it for `WALLET_V5_R1`, so it
         * is the only id that can be co-signed.
         */
        const val V5R1_WALLET_ID = 2147483409

        /**
         * Which wallet contract [senderAddress] is, for this key. [addressOf] returns the key's
         * address under a given contract, in any form [tonFriendlyToRaw] accepts.
         *
         * The keysign payload carries no wallet-version field, so the sender address is the
         * contract's identity: every co-signer derives both candidates from the shared vault key
         * and matches. An address that is neither is refused rather than assumed V4R2 — a pre-image
         * for the wrong contract can never match the initiator's. Mirrors SDK
         * `resolveTonWalletVersion`.
         */
        fun resolve(
            senderAddress: String,
            addressOf: (TonWalletVersion) -> String,
        ): TonWalletVersion {
            val account =
                requireNotNull(tonFriendlyToRaw(senderAddress)) {
                    "Invalid TON sender address $senderAddress"
                }
            return entries.firstOrNull { tonFriendlyToRaw(addressOf(it)) == account }
                ?: error(
                    "TON address $senderAddress is not this key's V4R2 or W5 wallet; " +
                        "refusing to sign for an unknown wallet contract"
                )
        }
    }
}

/**
 * The raw workchain-0 address of the contract a base64 StateInit BOC deploys: the hash of the
 * StateInit cell.
 */
internal fun tonStateInitAddress(stateInitBoc: String): String =
    tonAccountAddress(TonBocParser.parse(stateInitBoc))

internal fun tonAccountAddress(stateInit: TonCell): String =
    "0:" + stateInit.hash.joinToString("") { "%02x".format(it) }
