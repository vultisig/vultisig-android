package com.vultisig.wallet.data.blockchain

import JsonReader
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

class ChainHelpers {

    @Test
    fun sendBSCTest() {
        val fileName = "bsc.json"
        val appContext: Context = InstrumentationRegistry.getInstrumentation().context
        val data = JsonReader.readJsonFromAsset(appContext, fileName)

        println(data)
    }
}