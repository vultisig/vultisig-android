package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.models.SplTokenResponseJson

sealed interface SplTokenDeserialized {
    data class Error(val error: SplTokenResponseJson) : SplTokenDeserialized
    data class Result(val result: Map<String, SplTokenJson>) : SplTokenDeserialized
}