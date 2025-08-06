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
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import wallet.core.jni.CoinType

class ChainHelpersTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun sendBSCTest() {
        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data = JsonReader.readJsonFromAsset(appContext, BSC_JSON_FILE)
            ?: error("Failed sendBSCTest can't load payload $BSC_JSON_FILE")

        val transactions: List<TransactionData> = json.decodeFromString(data)

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
    @Ignore
    fun sendCosmosTest() {
        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data = JsonReader.readJsonFromAsset(appContext, COSMOS_JSON_FILE)
            ?: error("Failed sendBSCTest can't load payload $COSMOS_JSON_FILE")

        val transactions: List<TransactionData> = json.decodeFromString(data)
        val helper = CosmosHelper(CoinType.COSMOS, ATOM_DENOM)

        transactions.forEach { transaction ->
            val preImageHashes =
                helper.getPreSignedImageHash(transaction.keysignPayload.toInternalKeySignPayload())

            assertEquals(preImageHashes, transaction.expectedImageHash)
        }
    }


    private companion object {
        private const val BSC_JSON_FILE = "bsc.json"
        private const val COSMOS_JSON_FILE = "cosmos.json"

        private const val HEX_PUBLIC_KEY =
            "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b"
        private const val HEX_CHAIN_CODE =
            "c9b189a8232b872b8d9ccd867d0db316dd10f56e729c310fe072adf5fd204ae7"
    }
}