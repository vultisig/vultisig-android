package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

/** Represents a pending or active TonConnect v2 dApp session. */
@Serializable
data class TonConnectSession(
    /** The dApp's ephemeral public key, extracted from the `id` URI parameter. */
    val clientId: String,
    /** The TonConnect bridge URL; null until resolved from the request payload. */
    val bridgeUrl: String?,
    /** Base64url-encoded connect request payload from the `r` URI parameter. */
    val requestPayload: String,
    /** The vault ID chosen for this session; null until the user approves the connection. */
    val vaultId: String?,
)
