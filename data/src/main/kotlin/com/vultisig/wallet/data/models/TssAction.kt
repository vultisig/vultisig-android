package com.vultisig.wallet.data.models

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class TssAction {
    KEYGEN,
    ReShare,
    Migrate,
    KeyImport,
}
