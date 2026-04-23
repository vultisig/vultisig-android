package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

@Serializable data class TonKeysignSession(val vaultId: String, val signTonProtoBase64: String)
