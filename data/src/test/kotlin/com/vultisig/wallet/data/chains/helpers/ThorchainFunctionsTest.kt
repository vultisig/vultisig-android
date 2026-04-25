package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
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
    fun `stakeRUJI produces correct wasm payload with accountBond exec msg`() {
        val payload =
            ThorchainFunctions.stakeRUJI(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1contract",
                denom = "x/ruji",
                amount = BigInteger.valueOf(1_000_000L),
            )
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1contract", payload.contractAddress)
        assertEquals("""{ "account": { "bond": {} } }""", payload.executeMsg)
        assertEquals(1, payload.coins.size)
        assertEquals("x/ruji", payload.coins[0]!!.denom)
        assertEquals("1000000", payload.coins[0]!!.amount)
    }

    @Test
    fun `stakeRUJI throws on blank fromAddress or stakingContract or denom`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeRUJI("", "contract", "denom", BigInteger.ONE)
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeRUJI("addr", "", "denom", BigInteger.ONE)
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeRUJI("addr", "contract", "", BigInteger.ONE)
        }
    }

    @Test
    fun `unstakeRUJI produces correct wasm payload with accountWithdraw exec msg`() {
        val payload =
            ThorchainFunctions.unstakeRUJI(
                fromAddress = "cosmos1sender",
                amount = "500",
                stakingContract = "cosmos1contract",
            )
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1contract", payload.contractAddress)
        assertEquals("""{ "account": { "withdraw": { "amount": "500" } } }""", payload.executeMsg)
        assertTrue(payload.coins.isEmpty())
    }

    @Test
    fun `unstakeRUJI throws on blank fromAddress or stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unstakeRUJI("", "100", "contract")
        }
        assertThrows<IllegalArgumentException> { ThorchainFunctions.unstakeRUJI("addr", "100", "") }
    }

    @Test
    fun `claimRujiRewards produces correct wasm payload with accountClaim exec msg`() {
        val payload =
            ThorchainFunctions.claimRujiRewards(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1contract",
            )
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1contract", payload.contractAddress)
        assertEquals("""{ "account": { "claim": {} } }""", payload.executeMsg)
        assertTrue(payload.coins.isEmpty())
    }

    @Test
    fun `claimRujiRewards throws on blank fromAddress or stakingContract`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.claimRujiRewards("", "contract")
        }
        assertThrows<IllegalArgumentException> { ThorchainFunctions.claimRujiRewards("addr", "") }
    }

    @Test
    fun `mintYToken includes affiliate array and contract_addr in execute exec msg`() {
        val payload =
            try {
                ThorchainFunctions.mintYToken(
                    fromAddress = "cosmos1sender",
                    stakingContract = "cosmos1staking",
                    tokenContract = "cosmos1token",
                    denom = "uatom",
                    amount = BigInteger.valueOf(2_000_000L),
                )
            } catch (e: Throwable) {
                if (
                    e is UnsatisfiedLinkError ||
                        e is ExceptionInInitializerError ||
                        e is NoClassDefFoundError
                ) {
                    assumeTrue(false, "WalletCore JNI not available: ${e.message}")
                    return
                } else throw e
            }

        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1staking", payload.contractAddress)
        assertTrue(payload.executeMsg.contains(""""contract_addr":"cosmos1token""""))
        assertTrue(payload.executeMsg.contains(""""msg":"""))
        assertTrue(payload.executeMsg.contains(""""affiliate":["""))
        assertEquals(1, payload.coins.size)
        assertEquals("uatom", payload.coins[0]!!.denom)
        assertEquals("2000000", payload.coins[0]!!.amount)
    }

    @Test
    fun `mintYToken throws on blank fromAddress or stakingContract or tokenContract or denom`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.mintYToken("", "staking", "token", "denom", BigInteger.ONE)
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.mintYToken("addr", "", "token", "denom", BigInteger.ONE)
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.mintYToken("addr", "staking", "", "denom", BigInteger.ONE)
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.mintYToken("addr", "staking", "token", "", BigInteger.ONE)
        }
    }

    @Test
    fun `redeemYToken encodes slippage in withdraw exec msg`() {
        val payload =
            ThorchainFunctions.redeemYToken(
                fromAddress = "cosmos1sender",
                tokenContract = "cosmos1token",
                slippage = "50",
                denom = "x/ytcy",
                amount = BigInteger.valueOf(1_000_000L),
            )
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1token", payload.contractAddress)
        assertTrue(payload.executeMsg.contains(""""slippage":"50""""))
        assertEquals(1, payload.coins.size)
        assertEquals("x/ytcy", payload.coins[0]!!.denom)
        assertEquals("1000000", payload.coins[0]!!.amount)
    }

    @Test
    fun `redeemYToken throws on blank slippage`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.redeemYToken("addr", "token", "", "denom", BigInteger.ONE)
        }
    }

    @Test
    fun `stakeTcyCompound produces liquid bond exec msg with TCY denom`() {
        val payload =
            ThorchainFunctions.stakeTcyCompound(
                fromAddress = "cosmos1sender",
                stakingContract = "cosmos1contract",
                denom = "x/staking-tcy",
                amount = BigInteger.valueOf(5_000_000L),
            )
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1contract", payload.contractAddress)
        assertEquals("""{ "liquid": { "bond": {} } }""", payload.executeMsg)
        assertEquals(1, payload.coins.size)
        assertEquals("x/staking-tcy", payload.coins[0]!!.denom)
        assertEquals("5000000", payload.coins[0]!!.amount)
    }

    @Test
    fun `stakeTcyCompound throws on blank addresses or denom`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeTcyCompound("", "contract", "denom", BigInteger.ONE)
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.stakeTcyCompound("addr", "", "denom", BigInteger.ONE)
        }
    }

    @Test
    fun `unStakeTcyCompound uses TCY denom and encodes units as coin amount`() {
        val payload =
            ThorchainFunctions.unStakeTcyCompound(
                units = BigInteger.valueOf(42L),
                stakingContract = "cosmos1contract",
                fromAddress = "cosmos1sender",
            )
        assertEquals("cosmos1sender", payload.senderAddress)
        assertEquals("cosmos1contract", payload.contractAddress)
        assertEquals("""{ "liquid": { "unbond": {} } }""", payload.executeMsg)
        assertEquals(1, payload.coins.size)
        assertEquals("x/staking-tcy", payload.coins[0]!!.denom)
        assertEquals("42", payload.coins[0]!!.amount)
    }

    @Test
    fun `unStakeTcyCompound throws when units is zero or below one`() {
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unStakeTcyCompound(BigInteger.ZERO, "contract", "addr")
        }
        assertThrows<IllegalArgumentException> {
            ThorchainFunctions.unStakeTcyCompound(BigInteger.valueOf(-1L), "contract", "addr")
        }
    }
}
