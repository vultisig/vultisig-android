package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

@Serializable data class RootData(val node: AccountNode?)

@Serializable data class AccountNode(val merge: MergeInfo?, val stakingV2: List<StakingV2?>?)

@Serializable data class MergeInfo(val accounts: List<MergeAccount>)

@Serializable data class MergeAccount(val pool: Pool?, val size: Size?, val shares: String?)

@Serializable data class Pool(val mergeAsset: MergeAsset?, val summary: Summary? = null)

@Serializable data class MergeAsset(val metadata: Metadata?)

@Serializable data class Metadata(val symbol: String?)

@Serializable data class Size(val amount: String?)

@Serializable
data class StakingV2(
    val account: String,
    val bonded: Bonded,
    val pendingRevenue: PendingRevenue?,
    val pool: Pool?,
)

@Serializable data class Bonded(val amount: String, val asset: Asset)

@Serializable data class PendingRevenue(val amount: String, val asset: Asset)

@Serializable data class Asset(val metadata: Metadata? = null)

@Serializable data class Summary(val apr: Apr? = null)

@Serializable data class Apr(val value: String? = null)
