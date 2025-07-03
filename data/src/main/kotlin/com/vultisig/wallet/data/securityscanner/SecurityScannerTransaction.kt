package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

data class SecurityScannerTransaction(
    val chain: Chain,
    val type: SecurityTransactionType,
    val from: String,
    val to: String,
    val amount: BigInteger = BigInteger.ZERO,
    val data: String = "",
    val metadata: Map<String, String>,
)

enum class SecurityTransactionType {
    CoinTransfer,
    TokenTransfer,
    Swap,
    Approval,
    SmartContractCall, // generic Call/Msg
}

