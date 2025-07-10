package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.KeysignPayload
import wallet.core.jni.Base58
import java.math.BigInteger

class SecurityScannerTransactionFactory : SecurityScannerTransactionFactoryContract {
    override fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        val chain = transaction.token.chain
        return when (chain.standard) {
            TokenStandard.EVM -> createEVMSecurityScannerTransaction(transaction)
            TokenStandard.SOL -> createSOLSecurityScannerTransaction(transaction)
            else -> error("Not supported")
        }
    }

    private fun createEVMSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        val transferType: SecurityTransactionType
        val amount: BigInteger
        val data: String
        val to: String

        if (transaction.token.contractAddress.isNotEmpty()) {
            val tokenAmount = transaction.tokenValue.value
            transferType = SecurityTransactionType.TOKEN_TRANSFER
            amount = BigInteger.ZERO
            data = EthereumFunction.transferErc20(transaction.dstAddress, tokenAmount)
            to = transaction.token.contractAddress
        } else {
            transferType = SecurityTransactionType.COIN_TRANSFER
            amount = transaction.tokenValue.value
            data = "0x"
            to = transaction.dstAddress
        }

        return SecurityScannerTransaction(
            chain = transaction.token.chain,
            type = transferType,
            from = transaction.srcAddress,
            to = to,
            amount = amount,
            data = data,
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun createSOLSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        val vaultHexPubKey = Base58.decodeNoCheck(transaction.srcAddress).toHexString()
        val solanaHelper = SolanaHelper(vaultHexPubKey)

        val keySignPayload = KeysignPayload(
            coin = transaction.token,
            toAddress = transaction.dstAddress,
            toAmount = transaction.tokenValue.value,
            blockChainSpecific = transaction.blockChainSpecific,
            memo = transaction.memo,
            vaultPublicKeyECDSA = "", // no need for SOL prehash
            vaultLocalPartyID = "", // no need for SOL prehash
            libType = null, // no need for SOL prehash
        )

        val transactionZeroX = solanaHelper.getZeroSignedTransaction(keySignPayload)

        return SecurityScannerTransaction(
            chain = transaction.token.chain,
            type = SecurityTransactionType.COIN_TRANSFER,
            from = transaction.srcAddress,
            to = transaction.dstAddress,
            amount = BigInteger.ZERO, // encoded in tx
            data = transactionZeroX,
        )
    }
}