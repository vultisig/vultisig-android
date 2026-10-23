package com.vultisig.wallet.data.blockchain.cosmos.qbtc

/**
 * The QBTC network this build signs for. Send, vote, staking and claim all read from here, so a
 * network relaunch changes one value instead of several copies that can drift apart.
 */
object QbtcNetworkConfig {
    /** Cosmos chain id, signed into every QBTC transaction and hashed into the claim message. */
    const val CHAIN_ID = "qbtc"

    /** QBTC REST (LCD) base URL, behind the Vultisig proxy. */
    const val REST_URL = "https://api.vultisig.com/qbtc-rpc"

    /** QBTC claim proof service base URL, behind the Vultisig proxy. */
    const val PROOF_SERVICE_URL = "https://api.vultisig.com/qbtc-proof"
}
