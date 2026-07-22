package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.sui.SuiFeeService.Companion.SUI_DEFAULT_GAS_BUDGET
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import kotlinx.coroutines.coroutineScope
import vultisig.keysign.v1.SignSui
import wallet.core.jni.Base58
import wallet.core.jni.CoinType

class SecurityScannerTransactionFactory(
    private val solanaApi: SolanaApi,
    private val suiApi: SuiApi,
) : SecurityScannerTransactionFactoryContract {
    override suspend fun createSecurityScannerTransaction(
        transaction: Transaction
    ): SecurityScannerTransaction {
        val chain = transaction.token.chain
        return when (chain.standard) {
            TokenStandard.EVM -> createEVMSecurityScannerTransaction(transaction)
            TokenStandard.SOL -> createSOLSecurityScannerTransaction(transaction)
            TokenStandard.SUI -> createSUISecurityScannerTransaction(transaction)
            TokenStandard.UTXO -> createBTCSecurityScannerTransaction(transaction)
            else -> throw SecurityScannerException("Security Scanner: Not supported ${chain.name}")
        }
    }

    override suspend fun createSecurityScannerTransaction(
        transaction: SwapTransaction
    ): SecurityScannerTransaction {
        val chain = transaction.srcToken.chain
        return when (chain.standard) {
            TokenStandard.EVM -> createEVMSecurityScannerTransaction(transaction)
            else -> throw SecurityScannerException("Security Scanner: Not supported ${chain.name}")
        }
    }

    private fun createEVMSecurityScannerTransaction(
        transaction: SwapTransaction
    ): SecurityScannerTransaction {
        return when (val payload = transaction.payload) {
            is SwapPayload.EVM ->
                buildSwapSecurityScannerTransaction(
                    srcToken = transaction.srcToken,
                    from = payload.data.quote.tx.from,
                    to = payload.data.quote.tx.to,
                    // Scan the approval against the real allowance target (SwapKit's token-transfer
                    // proxy), which can differ from the swap `to`; fall back to `to` for providers
                    // where they coincide (1inch/Kyber/LiFi).
                    approveSpender =
                        payload.data.quote.tx.allowanceTarget ?: payload.data.quote.tx.to,
                    // The approval the user actually signs is for the swap input amount
                    // (KeysignShareViewModel uses transaction.srcTokenValue.value), not tx.value —
                    // which is 0 for an ERC20 swap. Scan the real approve amount so the preview
                    // matches the signed approve(spender, amount).
                    approveAmount = transaction.srcTokenValue.value,
                    amount = payload.data.quote.tx.value,
                    data = payload.data.quote.tx.data,
                    isApprovalRequired = transaction.isApprovalRequired,
                )

            else -> throw SecurityScannerException("Not supported provider for EVM")
        }
    }

    private fun buildSwapSecurityScannerTransaction(
        srcToken: Coin,
        from: String,
        to: String,
        approveSpender: String,
        approveAmount: BigInteger,
        amount: String,
        data: String,
        isApprovalRequired: Boolean,
    ): SecurityScannerTransaction {
        val chain = srcToken.chain

        return if (isApprovalRequired) {
            SecurityScannerTransaction(
                chain = chain,
                type = SecurityTransactionType.SWAP,
                from = from,
                to = srcToken.contractAddress,
                amount = BigInteger.ZERO,
                data = EthereumFunction.approvalErc20Encoder(approveSpender, approveAmount),
            )
        } else {
            SecurityScannerTransaction(
                chain = chain,
                type = SecurityTransactionType.SWAP,
                from = from,
                to = to,
                amount = amount.toBigInteger(),
                data = data,
            )
        }
    }

    private fun createEVMSecurityScannerTransaction(
        transaction: Transaction
    ): SecurityScannerTransaction {
        val transferType: SecurityTransactionType
        val amount: BigInteger
        val data: String
        val to: String

        if (transaction.token.contractAddress.isNotEmpty()) {
            val tokenAmount = transaction.tokenValue.value
            transferType = SecurityTransactionType.TOKEN_TRANSFER
            amount = BigInteger.ZERO
            data = EthereumFunction.transferErc20Encoder(transaction.dstAddress, tokenAmount)
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
    private suspend fun createSOLSecurityScannerTransaction(
        transaction: Transaction
    ): SecurityScannerTransaction = coroutineScope {
        val vaultHexPubKey = Base58.decodeNoCheck(transaction.srcAddress).toHexString()
        val solanaHelper = SolanaHelper(vaultHexPubKey)

        val solanaBlockchainSpecific = transaction.blockChainSpecific
        require(solanaBlockchainSpecific is BlockChainSpecific.Solana) {
            "Error Blockchain Specific is not Solana ${transaction.blockChainSpecific}"
        }

        val (solanaUpdatedSpecific, type) =
            if (transaction.token.isNativeToken) {
                transaction.blockChainSpecific to SecurityTransactionType.COIN_TRANSFER
            } else {
                val fromAddressPubKey =
                    solanaBlockchainSpecific.fromAddressPubKey
                        ?: solanaApi
                            .getTokenAssociatedAccountByOwner(
                                transaction.token.address,
                                transaction.token.contractAddress,
                            )
                            .first

                BlockChainSpecific.Solana(
                    recentBlockHash = solanaBlockchainSpecific.recentBlockHash,
                    priorityFee = solanaBlockchainSpecific.priorityFee,
                    fromAddressPubKey = fromAddressPubKey,
                    toAddressPubKey = solanaBlockchainSpecific.toAddressPubKey,
                    programId = solanaBlockchainSpecific.programId,
                    priorityLimit = solanaBlockchainSpecific.priorityLimit,
                ) to SecurityTransactionType.TOKEN_TRANSFER
            }

        val keySignPayload =
            KeysignPayload(
                coin = transaction.token,
                toAddress = transaction.dstAddress,
                toAmount = transaction.tokenValue.value,
                blockChainSpecific = solanaUpdatedSpecific,
                memo = transaction.memo,
                vaultPublicKeyECDSA = "", // no need for SOL prehash
                vaultLocalPartyID = "", // no need for SOL prehash
                libType = null, // no need for SOL prehash,
                wasmExecuteContractPayload = null,
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

    private suspend fun createSUISecurityScannerTransaction(
        transaction: Transaction
    ): SecurityScannerTransaction {
        val keySignPayload = buildSuiKeysignPayload(transaction)
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

    /**
     * Split out from [createSUISecurityScannerTransaction] so the branch selection is testable
     * without WalletCore's JNI signer. A dApp-supplied PTB (`signSui`) is self-contained and must
     * be scanned verbatim — that branch is taken before touching `blockChainSpecific`/`suiApi` so
     * the Blockaid scan reflects the real signed bytes instead of a reconstructed Pay/PaySui built
     * from unrelated RPC coins.
     */
    internal suspend fun buildSuiKeysignPayload(transaction: Transaction): KeysignPayload {
        val unsignedTxMsg = transaction.signSui
        if (unsignedTxMsg != null) {
            return KeysignPayload(
                coin = transaction.token,
                toAddress = transaction.dstAddress,
                toAmount = transaction.tokenValue.value,
                blockChainSpecific =
                    BlockChainSpecific.Sui(
                        referenceGasPrice = BigInteger.ZERO,
                        gasBudget = BigInteger.ZERO,
                        coins = emptyList(),
                    ),
                memo = transaction.memo,
                vaultPublicKeyECDSA = "", // no need for SUI prehash
                vaultLocalPartyID = "", // no need for SUI prehash
                libType = null, // no need for SUI prehash
                wasmExecuteContractPayload = null,
                signSui = SignSui(unsignedTxMsg = unsignedTxMsg),
            )
        }

        val suiBlockchainSpecific = transaction.blockChainSpecific
        require(suiBlockchainSpecific is BlockChainSpecific.Sui) {
            "Error Blockchain Specific is not SUI ${transaction.blockChainSpecific}"
        }
        val coins =
            suiBlockchainSpecific.coins.ifEmpty { suiApi.getAllCoins(transaction.srcAddress) }

        return KeysignPayload(
            coin = transaction.token,
            toAddress = transaction.dstAddress,
            toAmount = transaction.tokenValue.value,
            blockChainSpecific =
                BlockChainSpecific.Sui(
                    referenceGasPrice = suiBlockchainSpecific.referenceGasPrice,
                    coins = coins,
                    gasBudget = SUI_DEFAULT_GAS_BUDGET,
                ),
            memo = transaction.memo,
            vaultPublicKeyECDSA = "", // no need for SUI prehash
            vaultLocalPartyID = "", // no need for SUI prehash
            libType = null, // no need for SUI prehash
            wasmExecuteContractPayload = null,
        )
    }

    private fun createBTCSecurityScannerTransaction(
        transaction: Transaction
    ): SecurityScannerTransaction {
        val keySignPayload =
            KeysignPayload(
                coin = transaction.token,
                toAddress = transaction.dstAddress,
                toAmount = transaction.tokenValue.value,
                blockChainSpecific = transaction.blockChainSpecific,
                utxos = transaction.utxos,
                memo = transaction.memo,
                vaultPublicKeyECDSA = "", // no need for BTC
                vaultLocalPartyID = "", // no need for BTC
                libType = null, // no need for BTC
                wasmExecuteContractPayload = null,
            )

        val dummyVault =
            Vault(
                id = "dummy",
                name = "dummy",
                pubKeyECDSA = "0000000000000000000000000000000000000000000000000000000000000000",
                hexChainCode = "0000000000000000000000000000000000000000000000000000000000000000",
            )

        val btcHelper = UtxoHelper.getHelper(dummyVault, CoinType.BITCOIN)
        val zeroSignedTx = btcHelper.getZeroSignedTransaction(keySignPayload)

        return SecurityScannerTransaction(
            chain = transaction.token.chain,
            type = SecurityTransactionType.COIN_TRANSFER,
            from = transaction.srcAddress,
            to = transaction.dstAddress,
            amount = BigInteger.ZERO,
            data = zeroSignedTx,
        )
    }
}
