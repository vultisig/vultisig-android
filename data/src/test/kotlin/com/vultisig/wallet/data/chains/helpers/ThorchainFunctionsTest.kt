package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ThorchainFunctionsTest {

    @Test
    fun `rujiRewardsMemo builds correct memo string`() {
        val memo =
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "cosmos1contractabc",
                tokenAmountInt = BigInteger.valueOf(1_000_000L),
            )
        assertEquals("claim:cosmos1contractabc:1000000", memo)
    }

    @Test
    fun `rujiRewardsMemo handles zero amount`() {
        val memo =
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "cosmos1contract",
                tokenAmountInt = BigInteger.ZERO,
            )
        assertEquals("claim:cosmos1contract:0", memo)
    }

    @Test
    fun `tcyUnstakeMemo builds correct memo string`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 5000)
        assertEquals("TCY-:5000", memo)
    }

    @Test
    fun `tcyUnstakeMemo handles zero basis points`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 0)
        assertEquals("TCY-:0", memo)
    }

    @Test
    fun `tcyUnstakeMemo handles max basis points`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 10000)
        assertEquals("TCY-:10000", memo)
    }

    @Test
    fun `rujiRewardsMemo throws on blank contractAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "  ",
                tokenAmountInt = BigInteger.ONE,
            )
        }
    }

    @Test
    fun `rujiRewardsMemo throws on negative tokenAmountInt`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "addr",
                tokenAmountInt = BigInteger.valueOf(-1),
            )
        }
    }

    @Test
    fun `tcyUnstakeMemo throws on out-of-range basisPoints`() {
        assertThrows<IllegalArgumentException> { ThorchainFunctions.tcyUnstakeMemo(-1) }
        assertThrows<IllegalArgumentException> { ThorchainFunctions.tcyUnstakeMemo(10001) }
    }

    @Test
    fun `stakeRUJI builds payload with correct contract address and bond message`() {
        val payload =
            ThorchainFunctions.stakeRUJI(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1staking",
                denom = "uruji",
                amount = BigInteger.valueOf(1_000_000L),
            )
        assertEquals("cosmos1staking", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("""{ "account": { "bond": {} } }""", payload.executeMsg)
        assertEquals(1, payload.coins.size)
        assertEquals("uruji", payload.coins[0].denom)
        assertEquals("1000000", payload.coins[0].amount)
    }

    @Test
    fun `stakeRUJI throws on empty fromAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeRUJI(
                fromAddress = "",
                stakingContract = "cosmos1staking",
                denom = "uruji",
                amount = BigInteger.ONE,
            )
        }
    }

    @Test
    fun `stakeRUJI throws on empty stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeRUJI(
                fromAddress = "cosmos1sender",
                stakingContract = "",
                denom = "uruji",
                amount = BigInteger.ONE,
            )
        }
    }

    @Test
    fun `unstakeRUJI builds payload with withdraw amount and no coins`() {
        val payload =
            ThorchainFunctions.unstakeRUJI(
                fromAddress = "cosmos1sender",
                amount = "500000",
                stakingContract = "cosmos1staking",
            )
        assertEquals("cosmos1staking", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals(
            """{ "account": { "withdraw": { "amount": "500000" } } }""",
            payload.executeMsg,
        )
        assertEquals(0, payload.coins.size)
    }

    @Test
    fun `unstakeRUJI throws on empty fromAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unstakeRUJI(
                fromAddress = "",
                amount = "500000",
                stakingContract = "cosmos1staking",
            )
        }
    }

    @Test
    fun `unstakeRUJI throws on empty stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unstakeRUJI(
                fromAddress = "cosmos1sender",
                amount = "500000",
                stakingContract = "",
            )
        }
    }

    @Test
    fun `claimRujiRewards builds payload with claim message and no coins`() {
        val payload =
            ThorchainFunctions.claimRujiRewards(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1staking",
            )
        assertEquals("cosmos1staking", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("""{ "account": { "claim": {} } }""", payload.executeMsg)
        assertEquals(0, payload.coins.size)
    }

    @Test
    fun `claimRujiRewards throws on empty fromAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.claimRujiRewards(
                fromAddress = "",
                stakingContract = "cosmos1staking",
            )
        }
    }

    @Test
    fun `claimRujiRewards throws on empty stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.claimRujiRewards(fromAddress = "cosmos1sender", stakingContract = "")
        }
    }

    @Test
    fun `redeemYToken builds payload with slippage in execute message`() {
        val payload =
            ThorchainFunctions.redeemYToken(
                fromAddress = "cosmos1sender",
                tokenContract = "cosmos1token",
                slippage = "1.5",
                denom = "yrune",
                amount = BigInteger.valueOf(200_000L),
            )
        assertEquals("cosmos1token", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        val json = JSONObject(payload.executeMsg)
        assertEquals("1.5", json.getJSONObject("withdraw").getString("slippage"))
        assertEquals(1, payload.coins.size)
        assertEquals("yrune", payload.coins[0].denom)
        assertEquals("200000", payload.coins[0].amount)
    }

    @Test
    fun `redeemYToken throws on empty slippage`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.redeemYToken(
                fromAddress = "cosmos1sender",
                tokenContract = "cosmos1token",
                slippage = "",
                denom = "yrune",
                amount = BigInteger.ONE,
            )
        }
    }

    @Test
    fun `redeemYToken throws on empty fromAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.redeemYToken(
                fromAddress = "",
                tokenContract = "cosmos1token",
                slippage = "1.0",
                denom = "yrune",
                amount = BigInteger.ONE,
            )
        }
    }

    @Test
    fun `stakeTcyCompound builds payload with liquid-bond message and provided denom`() {
        val payload =
            ThorchainFunctions.stakeTcyCompound(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1staking",
                denom = "x/staking-tcy",
                amount = BigInteger.valueOf(5_000_000L),
            )
        assertEquals("cosmos1staking", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("""{ "liquid": { "bond": {} } }""", payload.executeMsg)
        assertEquals(1, payload.coins.size)
        assertEquals("x/staking-tcy", payload.coins[0].denom)
        assertEquals("5000000", payload.coins[0].amount)
    }

    @Test
    fun `stakeTcyCompound throws on empty fromAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeTcyCompound(
                fromAddress = "",
                stakingContract = "cosmos1staking",
                denom = "x/staking-tcy",
                amount = BigInteger.ONE,
            )
        }
    }

    @Test
    fun `unStakeTcyCompound builds payload with liquid-unbond message using TCY denom`() {
        val payload =
            ThorchainFunctions.unStakeTcyCompound(
                units = BigInteger.valueOf(1_000_000L),
                stakingContract = "cosmos1staking",
                fromAddress = "cosmos1sender",
            )
        assertEquals("cosmos1staking", payload.contractAddress)
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("""{ "liquid": { "unbond": {} } }""", payload.executeMsg)
        assertEquals(1, payload.coins.size)
        assertEquals("x/staking-tcy", payload.coins[0].denom)
        assertEquals("1000000", payload.coins[0].amount)
    }

    @Test
    fun `unStakeTcyCompound throws when units is zero`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unStakeTcyCompound(
                units = BigInteger.ZERO,
                stakingContract = "cosmos1staking",
                fromAddress = "cosmos1sender",
            )
        }
    }

    @Test
    fun `unStakeTcyCompound throws when units is negative`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unStakeTcyCompound(
                units = BigInteger.valueOf(-1L),
                stakingContract = "cosmos1staking",
                fromAddress = "cosmos1sender",
            )
        }
    }

    @Test
    fun `unStakeTcyCompound throws on empty stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unStakeTcyCompound(
                units = BigInteger.ONE,
                stakingContract = "",
                fromAddress = "cosmos1sender",
            )
        }
    }
}
