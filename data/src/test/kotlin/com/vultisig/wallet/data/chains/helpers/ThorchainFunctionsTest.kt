package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Unit tests for [ThorchainFunctions] memo builders and CosmWasm payload constructors. */
class ThorchainFunctionsTest {

    /** Verifies [ThorchainFunctions.rujiRewardsMemo] produces the expected claim memo format. */
    @Test
    fun `rujiRewardsMemo builds correct memo string`() {
        val memo =
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "cosmos1contractabc",
                tokenAmountInt = BigInteger.valueOf(1_000_000L),
            )
        assertEquals("claim:cosmos1contractabc:1000000", memo)
    }

    /** Verifies [ThorchainFunctions.rujiRewardsMemo] handles a zero token amount. */
    @Test
    fun `rujiRewardsMemo handles zero amount`() {
        val memo =
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "cosmos1contract",
                tokenAmountInt = BigInteger.ZERO,
            )
        assertEquals("claim:cosmos1contract:0", memo)
    }

    /** Verifies [ThorchainFunctions.tcyUnstakeMemo] produces the expected TCY unstake memo. */
    @Test
    fun `tcyUnstakeMemo builds correct memo string`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 5000)
        assertEquals("TCY-:5000", memo)
    }

    /** Verifies [ThorchainFunctions.tcyUnstakeMemo] accepts zero basis points. */
    @Test
    fun `tcyUnstakeMemo handles zero basis points`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 0)
        assertEquals("TCY-:0", memo)
    }

    /**
     * Verifies [ThorchainFunctions.tcyUnstakeMemo] accepts the maximum basis-point value (10000).
     */
    @Test
    fun `tcyUnstakeMemo handles max basis points`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 10000)
        assertEquals("TCY-:10000", memo)
    }

    /** Verifies [ThorchainFunctions.rujiRewardsMemo] rejects a blank contract address. */
    @Test
    fun `rujiRewardsMemo throws on blank contractAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "  ",
                tokenAmountInt = BigInteger.ONE,
            )
        }
    }

    /** Verifies [ThorchainFunctions.rujiRewardsMemo] rejects a negative token amount. */
    @Test
    fun `rujiRewardsMemo throws on negative tokenAmountInt`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "addr",
                tokenAmountInt = BigInteger.valueOf(-1),
            )
        }
    }

    /** Verifies [ThorchainFunctions.tcyUnstakeMemo] rejects basis points outside [0, 10000]. */
    @Test
    fun `tcyUnstakeMemo throws on out-of-range basisPoints`() {
        assertThrows<IllegalArgumentException> { ThorchainFunctions.tcyUnstakeMemo(-1) }
        assertThrows<IllegalArgumentException> { ThorchainFunctions.tcyUnstakeMemo(10001) }
    }

    /**
     * Verifies [ThorchainFunctions.stakeRUJI] builds a CosmWasm payload with a bond execute
     * message.
     */
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
        val coin0 = payload.coins[0]!!
        assertEquals("uruji", coin0.denom)
        assertEquals("1000000", coin0.amount)
    }

    /** Verifies [ThorchainFunctions.stakeRUJI] rejects an empty sender address. */
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

    /** Verifies [ThorchainFunctions.stakeRUJI] rejects an empty staking contract address. */
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

    /** Verifies [ThorchainFunctions.unstakeRUJI] builds a withdraw payload with no native coins. */
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

    /** Verifies [ThorchainFunctions.unstakeRUJI] rejects an empty sender address. */
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

    /** Verifies [ThorchainFunctions.unstakeRUJI] rejects an empty staking contract address. */
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

    /**
     * Verifies [ThorchainFunctions.claimRujiRewards] builds a claim payload with no native coins.
     */
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

    /** Verifies [ThorchainFunctions.claimRujiRewards] rejects an empty sender address. */
    @Test
    fun `claimRujiRewards throws on empty fromAddress`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.claimRujiRewards(
                fromAddress = "",
                stakingContract = "cosmos1staking",
            )
        }
    }

    /** Verifies [ThorchainFunctions.claimRujiRewards] rejects an empty staking contract address. */
    @Test
    fun `claimRujiRewards throws on empty stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.claimRujiRewards(fromAddress = "cosmos1sender", stakingContract = "")
        }
    }

    /**
     * Verifies [ThorchainFunctions.redeemYToken] builds a payload encoding the slippage in the
     * execute message.
     */
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
        assertTrue(payload.executeMsg.contains(""""slippage":"1.5""""))
        assertEquals(1, payload.coins.size)
        val coin0 = payload.coins[0]!!
        assertEquals("yrune", coin0.denom)
        assertEquals("200000", coin0.amount)
    }

    /** Verifies [ThorchainFunctions.redeemYToken] rejects an empty slippage string. */
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

    /** Verifies [ThorchainFunctions.redeemYToken] rejects an empty sender address. */
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

    /**
     * Verifies [ThorchainFunctions.stakeTcyCompound] builds a liquid-bond payload with the given
     * denom.
     */
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
        val coin0 = payload.coins[0]!!
        assertEquals("x/staking-tcy", coin0.denom)
        assertEquals("5000000", coin0.amount)
    }

    /** Verifies [ThorchainFunctions.stakeTcyCompound] rejects an empty sender address. */
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

    /**
     * Verifies [ThorchainFunctions.unStakeTcyCompound] builds a liquid-unbond payload with the TCY
     * denom.
     */
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
        val coin0 = payload.coins[0]!!
        assertEquals("x/staking-tcy", coin0.denom)
        assertEquals("1000000", coin0.amount)
    }

    /** Verifies [ThorchainFunctions.unStakeTcyCompound] rejects zero units. */
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

    /** Verifies [ThorchainFunctions.unStakeTcyCompound] rejects negative units. */
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

    /**
     * Verifies [ThorchainFunctions.unStakeTcyCompound] rejects an empty staking contract address.
     */
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
