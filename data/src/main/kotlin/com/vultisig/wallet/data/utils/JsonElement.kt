package com.vultisig.wallet.data.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

val JsonElement.contentOrNull: String?
    get() = (this as? JsonPrimitive)?.contentOrNull