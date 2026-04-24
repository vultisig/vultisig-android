package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.json.JSONObject
import org.junit.Test

class ThorchainFunctionsMintTest {

    @Test
    fun mintYToken_payload_includes_execute_with_contractAddr_base64Msg_and_affiliate() {
        val payload =
            ThorchainFunctions.mintYToken(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1staking",
                tokenContract = "cosmos1token",
                denom = "uruji",
                amount = BigInteger.valueOf(1_000_000L),
            )

        assertEquals("cosmos1staking", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals(1, payload.coins.size)
        assertEquals("uruji", payload.coins[0].denom)
        assertEquals("1000000", payload.coins[0].amount)

        val execute = JSONObject(payload.executeMsg).getJSONObject("execute")
        assertEquals("cosmos1token", execute.getString("contract_addr"))
        assertNotNull(execute.getString("msg"))
        val affiliate = execute.getJSONArray("affiliate")
        assertEquals(2, affiliate.length())
        assertEquals("thor1svfwxevnxtm4ltnw92hrqpqk4vzuzw9a4jzy04", affiliate.getString(0))
        assertEquals(10, affiliate.getInt(1))
    }
}
