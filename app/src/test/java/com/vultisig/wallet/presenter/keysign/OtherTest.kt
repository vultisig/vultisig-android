package com.vultisig.wallet.presenter.keysign
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.data.api.RpcResponse
import org.junit.Test

class OtherTest {
    @Test
    fun testSolanaRpc(){
        val input = """
            {
                "jsonrpc": "2.0",
                "result": {
                    "context": {
                        "apiVersion": "1.17.28",
                        "slot": 265454817
                    },
                    "value": 0
                },
                "id": 1
            }
        """
        val result = Gson().fromJson(input, JsonObject::class.java)
        println(result.get("result")?.asJsonObject?.get("value").toString())
    }
}