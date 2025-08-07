package com.vultisig.wallet.data.blockchain

import JsonReader
import TransactionData
import android.content.Context
import androidx.room.Ignore
import androidx.test.platform.app.InstrumentationRegistry
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.chains.helpers.CosmosHelper.Companion.ATOM_DENOM
import com.vultisig.wallet.data.chains.helpers.ERC20Helper
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.RippleHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.TerraHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import wallet.core.jni.CoinType

class ChainHelpersTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun sendBSCTest() {
        val transactions: List<TransactionData> = loadTransactionData(BSC_JSON_FILE)

        val helper = EvmHelper(CoinType.SMARTCHAIN, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
        val erc20Helper = ERC20Helper(CoinType.SMARTCHAIN, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes = if (transaction.keysignPayload.coin.isNativeToken) {
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())
            } else {
                erc20Helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())
            }
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendEVMTest() {
        val transactions: List<TransactionData> = loadTransactionData(EVM_JSON_FILE)

        val helper = EvmHelper(CoinType.ETHEREUM, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
        val erc20Helper = ERC20Helper(CoinType.ETHEREUM, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes = if (transaction.keysignPayload.coin.isNativeToken) {
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())
            } else {
                erc20Helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())
            }
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendXRPTest() {
        val transactions: List<TransactionData> = loadTransactionData(XRP_JSON_FILE)

        transactions.forEach { transaction ->
            val preImageHashes =
                RippleHelper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTONTest() {
        val transactions: List<TransactionData> = loadTransactionData(TON_JSON_FILE)

        transactions.forEach { transaction ->
            val preImageHashes =
                TonHelper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())
            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendCosmosTest() {
        val transactions: List<TransactionData> = loadTransactionData(COSMOS_JSON_FILE)
        val helper = CosmosHelper(CoinType.COSMOS, ATOM_DENOM)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendSolana() {
        val transactions: List<TransactionData> = loadTransactionData(SOLANA_JSON_FILE)
        val helper = SolanaHelper(HEX_PUBLIC_KEY)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTerra() {
        val transactions: List<TransactionData> = loadTransactionData(TERRA_JSON_FILE)

        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val coin = payload.coin.coinType
            val helper = TerraHelper(coin, "uluna", 300000L)

            val preImageHashes =
                helper.getPreSignedImageHash(payload)

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendTHORchain() {
        val transactions: List<TransactionData> = loadTransactionData(THORCHAIN_JSON_FILE)
        val helper = ThorChainHelper.thor(HEX_PUBLIC_KEY, HEX_CHAIN_CODE)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    @Test
    fun sendUTXO() {
        val transactions: List<TransactionData> = loadTransactionData(UTXO_JSON_FILE)
        transactions.forEach { transaction ->
            val payload = transaction.keysignPayload.toInternalKeySignPayload()
            val coin = payload.coin.coinType

            val helper = UtxoHelper(coin, HEX_PUBLIC_KEY, HEX_CHAIN_CODE)
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }

    private fun loadTransactionData(jsonFile: String): List<TransactionData> {
        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data = JsonReader.readJsonFromAsset(appContext, jsonFile)
            ?: error("Failed can't load payload $jsonFile")
        return json.decodeFromString(data)
    }

    private companion object {
        private const val BSC_JSON_FILE = "bsc.json"
        private const val EVM_JSON_FILE = "evm.json"
        private const val COSMOS_JSON_FILE = "cosmos.json"
        private const val XRP_JSON_FILE = "xrp.json"
        private const val TON_JSON_FILE = "ton.json"
        private const val SOLANA_JSON_FILE = "solana.json"
        private const val THORCHAIN_JSON_FILE = "thorchain.json"
        private const val TERRA_JSON_FILE = "terra.json"
        private const val UTXO_JSON_FILE = "utxo.json"

        private const val HEX_PUBLIC_KEY =
            "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b"
        private const val HEX_CHAIN_CODE =
            "c9b189a8232b872b8d9ccd867d0db316dd10f56e729c310fe072adf5fd204ae7"
    }
}