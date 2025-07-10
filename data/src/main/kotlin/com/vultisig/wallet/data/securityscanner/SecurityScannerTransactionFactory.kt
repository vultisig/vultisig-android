package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import kotlinx.coroutines.coroutineScope
import wallet.core.jni.Base58
import wallet.core.jni.CoinType
import java.math.BigInteger

class SecurityScannerTransactionFactory(
    private val solanaApi: SolanaApi,
    private val suiApi: SuiApi,
    private val blockchainSpecificRepository: BlockChainSpecificRepository,
) : SecurityScannerTransactionFactoryContract {
    override suspend fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        val chain = transaction.token.chain
        return when (chain.standard) {
            TokenStandard.EVM -> createEVMSecurityScannerTransaction(transaction)
            TokenStandard.SOL -> createSOLSecurityScannerTransaction(transaction)
            TokenStandard.SUI -> createSUISecurityScannerTransaction(transaction)
            TokenStandard.UTXO -> createBTCSecurityScannerTransaction(transaction)
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
    private suspend fun createSOLSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction =
        coroutineScope {
            val vaultHexPubKey = Base58.decodeNoCheck(transaction.srcAddress).toHexString()
            val solanaHelper = SolanaHelper(vaultHexPubKey)

            val solanaBlockchainSpecific = transaction.blockChainSpecific
            require(solanaBlockchainSpecific is BlockChainSpecific.Solana) {
                "Error Blockchain Specific is not Solana ${transaction.blockChainSpecific}"
            }

            val (solanaUpdatedSpecific, type) = if (transaction.token.isNativeToken) {
                transaction.blockChainSpecific to SecurityTransactionType.COIN_TRANSFER
            } else {
                val fromAddressPubKey = solanaBlockchainSpecific.fromAddressPubKey
                    ?: solanaApi.getTokenAssociatedAccountByOwner(
                        transaction.token.address,
                        transaction.token.contractAddress
                    ).first

                BlockChainSpecific.Solana(
                    recentBlockHash = solanaBlockchainSpecific.recentBlockHash,
                    priorityFee = solanaBlockchainSpecific.priorityFee,
                    fromAddressPubKey = fromAddressPubKey,
                    toAddressPubKey = solanaBlockchainSpecific.toAddressPubKey,
                    programId = solanaBlockchainSpecific.programId,
                ) to SecurityTransactionType.TOKEN_TRANSFER
            }

            val keySignPayload = KeysignPayload(
                coin = transaction.token,
                toAddress = transaction.dstAddress,
                toAmount = transaction.tokenValue.value,
                blockChainSpecific = solanaUpdatedSpecific,
                memo = transaction.memo,
                vaultPublicKeyECDSA = "", // no need for SOL prehash
                vaultLocalPartyID = "", // no need for SOL prehash
                libType = null, // no need for SOL prehash
            )

            val transactionZeroX = solanaHelper.getZeroSignedTransaction(keySignPayload)

            SecurityScannerTransaction(
                chain = transaction.token.chain,
                type = type,
                from = transaction.srcAddress,
                to = transaction.dstAddress,
                amount = BigInteger.ZERO, // encoded in tx
                data = transactionZeroX,
            )
        }

    private suspend fun createSUISecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        val suiBlockchainSpecific = transaction.blockChainSpecific
        require(suiBlockchainSpecific is BlockChainSpecific.Sui) {
            "Error Blockchain Specific is not SUI ${transaction.blockChainSpecific}"
        }

        val coins = suiBlockchainSpecific.coins.ifEmpty {
            suiApi.getAllCoins(transaction.srcAddress)
        }
        val updatedSuiBlockChainSpecific = BlockChainSpecific.Sui(
            referenceGasPrice = suiBlockchainSpecific.referenceGasPrice,
            coins = coins,
        )

        val keySignPayload = KeysignPayload(
            coin = transaction.token,
            toAddress = transaction.dstAddress,
            toAmount = transaction.tokenValue.value,
            blockChainSpecific = updatedSuiBlockChainSpecific,
            memo = transaction.memo,
            vaultPublicKeyECDSA = "", // no need for SUI prehash
            vaultLocalPartyID = "", // no need for SUI prehash
            libType = null, // no need for SUI prehash
        )

        val serializedTransaction = SuiHelper.getZeroSignedTransaction(keySignPayload)

        return SecurityScannerTransaction(
            chain = transaction.token.chain,
            type = SecurityTransactionType.COIN_TRANSFER,
            from = transaction.srcAddress,
            to = transaction.dstAddress,
            amount = BigInteger.ZERO,
            data = serializedTransaction,
        )
    }

    // TODO: Review as it looks like it requires PSBT, which is not supported by WC legacy API
    private fun createBTCSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        val keySignPayload = KeysignPayload(
            coin = transaction.token,
            toAddress = transaction.dstAddress,
            toAmount = transaction.tokenValue.value,
            blockChainSpecific = transaction.blockChainSpecific,
            utxos = transaction.utxos,
            memo = transaction.memo,
            vaultPublicKeyECDSA = "", // no need for BTC
            vaultLocalPartyID = "", // no need for BTC
            libType = null, // no need for BTC
        )

        val btcHelper = UtxoHelper.getHelper(Vault("", ""), CoinType.BITCOIN)

        val preHash = btcHelper.getPreSignedImageHash(keySignPayload)

        return SecurityScannerTransaction(
            chain = transaction.token.chain,
            type = SecurityTransactionType.COIN_TRANSFER,
            from = transaction.srcAddress,
            to = transaction.dstAddress,
            amount = BigInteger.ZERO,
            data = preHash[0],
        )
    }
}