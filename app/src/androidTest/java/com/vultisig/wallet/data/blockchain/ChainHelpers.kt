package com.vultisig.wallet.data.blockchain

import JsonReader
import TransactionData
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import org.junit.Test

class ChainHelpers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun sendBSCTest() {
        val fileName = "bsc.json"
        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data = JsonReader.readJsonFromAsset(appContext, fileName)
            ?: error("Failed sendBSCTest can't load payload $fileName")

        val transactions: List<TransactionData> = json.decodeFromString(data)

        println(data)
    }
}