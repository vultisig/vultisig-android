package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Transaction
import timber.log.Timber
import java.math.BigInteger

internal suspend fun <T> runSecurityScan(
    transaction: SecurityScannerTransaction,
    block: suspend () -> T
): T {
    Timber.d("SecurityScanner: Scanning ${transaction.chain.name} transaction: $transaction")
    return try {
        val result = block()
        Timber.d("SecurityScanner: Result for ${transaction.chain.name} transaction: $result")
        result
    } catch (t: Throwable) {
        val errorMessage = "SecurityScanner: Error scanning ${transaction.chain.name}"
        Timber.e(t, errorMessage)
        throw SecurityScannerException(errorMessage, t, transaction.chain, transaction.toString())
    }
}

fun Transaction.toSecurityScannerTransaction(): SecurityScannerTransaction {
    val transferType: SecurityTransactionType
    val amount: BigInteger
    val data: String
    val to: String

    if (this.token.contractAddress.isNotEmpty()) {
        val tokenAmount = this.tokenValue.value
        transferType = SecurityTransactionType.TOKEN_TRANSFER
        amount = BigInteger.ZERO
        data = EthereumFunction.transferErc20(this.dstAddress, tokenAmount)
        to = this.token.contractAddress
    } else {
        transferType = SecurityTransactionType.COIN_TRANSFER
        amount = this.tokenValue.value
        data = "0x"
        to = this.dstAddress
    }

    return SecurityScannerTransaction(
        chain = this.token.chain,
        type = transferType,
        from = this.srcAddress,
        to = to,
        amount = amount,
        data = data,
    )
}

fun List<SecurityScannerSupport>.isChainSupported(chain: Chain): Boolean {
    return any { support ->
        support.feature.any { feature ->
            chain in feature.chains
        }
    }
}