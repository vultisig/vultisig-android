package com.vultisig.wallet.data.blockchain.polkadot

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger

class PolkadotFeeService(
    private val polkadotApi: PolkadotApi,
): FeeService {
    override suspend fun calculateFees(
        chain: Chain,
        limit: BigInteger,
        isSwap: Boolean,
        to: String?
    ): Fee {
        val address = ""
        val keySignPayload = buildPolkadotSpecific(address)
        val helper = PolkadotHelper("").getPreSignedImageHash(keySignPayload)

        val result = polkadotApi.queryInfo(helper.first())
        println(result)

        return BasicFee(POLKADOT_DEFAULT_FEE)
    }

    private suspend fun buildPolkadotSpecific(address: String): KeysignPayload = coroutineScope {
        val polkadotCoin = Coins.coins[Chain.Polkadot]
            ?.first { it.isNativeToken }
            ?: error("Polkadot Coin not found")

        val runtimeVersionDeferred = async { polkadotApi.getRuntimeVersion() }
        val blockHashDeferred = async { polkadotApi.getBlockHash() }
        val nonceDeferred = async { polkadotApi.getNonce(address) }
        val blockHeaderDeferred = async { polkadotApi.getBlockHeader() }
        val genesisHashDeferred = async { polkadotApi.getGenesisBlockHash() }

        val (specVersion, transactionVersion) = runtimeVersionDeferred.await()

        KeysignPayload(
            coin = polkadotCoin,
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific = BlockChainSpecific.Polkadot(
                recentBlockHash = blockHashDeferred.await(),
                nonce = nonceDeferred.await(),
                currentBlockNumber = blockHeaderDeferred.await(),
                specVersion = specVersion.toLong().toUInt(),
                transactionVersion = transactionVersion.toLong().toUInt(),
                genesisHash = genesisHashDeferred.await()
            ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    }

    override suspend fun calculateDefaultFees(): Fee {
        return BasicFee(POLKADOT_DEFAULT_FEE)
    }

    private companion object {
        val POLKADOT_DEFAULT_FEE = "250_000_000".toBigInteger()
    }
}