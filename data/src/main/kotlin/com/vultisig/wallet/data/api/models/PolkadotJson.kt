package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class PolkadotGetStorageJson(@SerialName("result") val result: String?)
