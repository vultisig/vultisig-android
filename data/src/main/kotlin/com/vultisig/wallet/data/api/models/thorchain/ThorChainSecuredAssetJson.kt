package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A THORChain secured asset, identified by its uppercase denom (`BTC-BTC`, `ETH-USDC-0XA0B8…`). */
@Serializable data class ThorChainSecuredAssetJson(@SerialName("asset") val asset: String)
