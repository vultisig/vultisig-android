package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.api.BlowfishApi
import com.vultisig.wallet.data.api.blowfishChainName
import com.vultisig.wallet.data.api.blowfishNetwork
import com.vultisig.wallet.data.api.models.BlowfishMetadata
import com.vultisig.wallet.data.api.models.BlowfishRequest
import com.vultisig.wallet.data.api.models.BlowfishResponse
import com.vultisig.wallet.data.api.models.BlowfishTxObject
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.KeysignPayload
import javax.inject.Inject

interface BlowfishRepository {

    suspend fun scanBlowfishTransaction(
        vault: Vault,
        transaction: Transaction
    ): Pair<Boolean, List<String>>
}

internal class BlowfishRepositoryImpl @Inject constructor(
    private val blowfishApi: BlowfishApi
) : BlowfishRepository {

    override suspend fun scanBlowfishTransaction(
        vault: Vault,
        transaction: Transaction
    ): Pair<Boolean, List<String>> {
        val chain = Chain.fromRaw(transaction.chainId)
        val chainType = chain.standard
        when (chainType) {
            TokenStandard.EVM -> {
                val result = scanEVMBlowfishTransaction(chain, transaction)
                return Pair(
                    true,
                    result.warnings?.mapNotNull { it.message } ?: emptyList(),
                )
            }

            TokenStandard.SOL -> {
                val result = scanBlowfishSolanaTransaction(vault, transaction)
                return Pair(
                    true,
                    result.aggregated?.warnings?.mapNotNull { it.message } ?: emptyList(),
                )
            }

            else -> { error("Chain is not supported by Blowfish") }
        }
    }
    private suspend fun scanBlowfishSolanaTransaction(
        vault: Vault,
        transaction: Transaction
    ): BlowfishResponse {
        val coin =
            vault.coins.find { it.id == transaction.tokenId && it.chain.id == transaction.chainId }!!

        val keysignPayload = KeysignPayload(
            coin = coin,
            toAddress = transaction.dstAddress,
            toAmount = transaction.tokenValue.value,
            blockChainSpecific = transaction.blockChainSpecific,
            memo = transaction.memo,
            swapPayload = null,
            approvePayload = null,
            vaultPublicKeyECDSA = vault.pubKeyECDSA,
            utxos = transaction.utxos,
            vaultLocalPartyID = vault.localPartyID,
        )

        val zeroSignedTransaction =
            SolanaHelper(vault.pubKeyEDDSA).getZeroSignedTransaction(keysignPayload)
        val blowfishRequest = BlowfishRequest(
            userAccount = transaction.srcAddress,
            metadata = BlowfishMetadata("https://api.vultisig.com"),
            txObjects = null,
            simulatorConfig = null,
            transactions = listOf(zeroSignedTransaction),
        )
        val result = blowfishApi.fetchBlowfishSolanaTransactions(blowfishRequest)
        return result
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun scanEVMBlowfishTransaction(
        chain: Chain,
        transaction: Transaction
    ): BlowfishResponse {
        val supportedChain = chain.blowfishChainName!!
        val supportedNetwork = chain.blowfishNetwork!!

        val amountHex =
            "0x" + transaction.tokenValue.value.toByteArray().toHexString()
        val memoDataHex =
            transaction.memo?.toBigInteger()?.toByteArray()?.toHexString() ?: ""
        val memoHex = "0x$memoDataHex"

        val blowfishRequest = BlowfishRequest(
            userAccount = transaction.srcAddress,
            metadata = BlowfishMetadata("https://api.vultisig.com"),
            txObjects = listOf(
                BlowfishTxObject(
                    from = transaction.srcAddress,
                    to = transaction.dstAddress,
                    value = amountHex,
                    data = memoHex,
                )
            ),
            simulatorConfig = null,
            transactions = null
        )

        val result = blowfishApi.fetchBlowfishTransactions(
            supportedChain,
            supportedNetwork,
            blowfishRequest
        )
        return result
    }
}