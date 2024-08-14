package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.SolanaHelper
import com.vultisig.wallet.data.api.BlowfishApi
import com.vultisig.wallet.data.api.models.BlowfishMetadata
import com.vultisig.wallet.data.api.models.BlowfishRequest
import com.vultisig.wallet.data.api.models.BlowfishResponse
import com.vultisig.wallet.data.api.models.BlowfishTxObject
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.blowfishChainName
import com.vultisig.wallet.models.blowfishNetwork
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import javax.inject.Inject

internal interface BlowfishRepository {
    suspend fun scanBlowfishSolanaTransaction(
        vault: Vault,
        transaction: Transaction
    ): BlowfishResponse

    suspend fun scanBlowfishTransaction(
        chain: Chain,
        transaction: Transaction
    ): BlowfishResponse
}

internal class BlowfishRepositoryImpl @Inject constructor(
    private val blowfishApi: BlowfishApi
) : BlowfishRepository {
    override suspend fun scanBlowfishSolanaTransaction(
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
        //8VLG45tdZg11XiVBQqHGpT4e4A5P5M8TFd47FqBdWtyL
        //scam - G6AhgwTL7gRoSTTPqUyx4koJVJZNfnNp4zZ43kWdmRr6
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
    override suspend fun scanBlowfishTransaction(
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