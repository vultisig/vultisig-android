package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

@Serializable
data class TonConnectSession(val vaultId: String, val clientId: String, val rawPayload: String)
