package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class TssAction {
    KEYGEN, ReShare, Migrate, KeyImport
}