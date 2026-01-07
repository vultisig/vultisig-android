package com.vultisig.wallet.data.api.models.maya

data class MayaNetworkInfoResponse(
    val bondingAPY: String,
)

/*
let bondingAPY: String?
    let nextChurnHeight: String?
    let totalPooledRune: String?  // Total CACAO in the pool (in atomic units)
    let liquidityAPY: String?     // CACAO pool APY

    enum CodingKeys: String, CodingKey {
        case bondingAPY = "bondingAPY"
        case nextChurnHeight = "nextChurnHeight"
        case totalPooledRune = "totalPooledRune"
        case liquidityAPY = "liquidityAPY"
    }

    {
  "bond_reward_cacao": "513080229014554",
  "total_bond_units": "9482748",
  "total_reserve": "84681464893257",
  "total_asgard": "507411313816150518",
  "gas_spent_cacao": "12618546244830850",
  "gas_withheld_cacao": "13646521626951653",
  "outbound_fee_multiplier": "10000"
}
 */