package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * Locks down the contract bridge between [CosmosStakingPayload] and the SignDoc bytes consumed by
 * Trust Wallet Core's signDirect path. Mirrors iOS `CosmosStakingSignDataResolverTests.swift`
 * (vultisig-ios PR #4432).
 *
 * Each test asserts behaviour at the resolver boundary — preflight rejection, missing-field
 * rejection, deterministic encoder output, linear gas/fee scaling, batched-claim cap enforcement.
 */
class CosmosStakingSignDataResolverTests {

    // MARK: - Fixtures
    //
    // Deterministic Terra address + a valid `terravaloper` validator generated via the bech32
    // test encoder used in [ValidatorBech32PreflightTests] — payload bytes 0..19 in increasing
    // order.

    private object FX {
        const val DELEGATOR = "terra1delegator00000000000000000000000000ab"
        const val PUBKEY_HEX = "020202020202020202020202020202020202020202020202020202020202020202"
        const val ACCOUNT_NUMBER = 100L
        const val SEQUENCE = 42L
        val VALIDATOR_A = makeValoper(seedByte = 0x0A)
        val VALIDATOR_B = makeValoper(seedByte = 0x0B)
        val VALIDATOR_C = makeValoper(seedByte = 0x0C)

        private fun makeValoper(seedByte: Int): String {
            val payload = ByteArray(20) { ((seedByte + it) and 0xFF).toByte() }
            return Bech32TestEncoder.encode("terravaloper", payload)
        }

        val COSMOS_SPECIFIC =
            BlockChainSpecific.Cosmos(
                accountNumber = BigInteger.valueOf(ACCOUNT_NUMBER),
                sequence = BigInteger.valueOf(SEQUENCE),
                gas = BigInteger.ZERO,
                ibcDenomTraces = null,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )
    }

    // MARK: - Delegate

    @Test
    fun `delegate produces SignDoc artefacts with Terra chain config baked in`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        val result =
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = Chain.Terra,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = FX.PUBKEY_HEX,
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        assertEquals("phoenix-1", result.chainId)
        assertEquals(FX.ACCOUNT_NUMBER.toString(), result.accountNumber)
        // bodyBytes must round-trip through the proto helper: re-encoding the delegate msg into a
        // fresh TxBody must produce identical bytes (no encoder drift).
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeDelegate(
                        delegator = FX.DELEGATOR,
                        validator = FX.VALIDATOR_A,
                        amount = "1000000",
                        denom = "uluna",
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(result.bodyBytes))
    }

    @Test
    fun `delegate is deterministic across repeated calls`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        val a = resolve(payload)
        val b = resolve(payload)
        assertEquals(a, b)
    }

    @Test
    fun `delegate rejects empty validator`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = "",
                denom = "uluna",
                amount = "1000000",
            )
        val ex =
            assertFailsWith<CosmosStakingSignDataResolver.ResolverException.MissingPayloadField> {
                resolve(payload)
            }
        assertEquals("validatorAddress", ex.field)
    }

    @Test
    fun `delegate rejects empty amount`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "",
            )
        val ex =
            assertFailsWith<CosmosStakingSignDataResolver.ResolverException.MissingPayloadField> {
                resolve(payload)
            }
        assertEquals("amount", ex.field)
    }

    @Test
    fun `delegate fails preflight on malformed validator`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = "not-a-valid-bech32",
                denom = "uluna",
                amount = "1000000",
            )
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.ValidatorPreflightFailed> {
            resolve(payload)
        }
    }

    @Test
    fun `delegate fails preflight when HRP is wrong for chain`() {
        // Cosmoshub valoper has the right structural shape but wrong HRP for Terra.
        val cosmoshub = Bech32TestEncoder.encode("cosmosvaloper", ByteArray(20) { it.toByte() })
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = cosmoshub,
                denom = "uluna",
                amount = "1000000",
            )
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.ValidatorPreflightFailed> {
            resolve(payload)
        }
    }

    // MARK: - Undelegate

    @Test
    fun `undelegate uses MsgUndelegate typeURL but same Terra config`() {
        val payload =
            CosmosStakingPayload.Undelegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "500000",
            )
        val result = resolve(payload)
        assertEquals("phoenix-1", result.chainId)
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeUndelegate(
                        delegator = FX.DELEGATOR,
                        validator = FX.VALIDATOR_A,
                        amount = "500000",
                        denom = "uluna",
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(result.bodyBytes))
    }

    // MARK: - Redelegate

    @Test
    fun `redelegate preflights both src and dst`() {
        // Valid src, invalid dst.
        val payload =
            CosmosStakingPayload.Redelegate(
                validatorSrcAddress = FX.VALIDATOR_A,
                validatorDstAddress = "not-bech32",
                denom = "uluna",
                amount = "100000",
            )
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.ValidatorPreflightFailed> {
            resolve(payload)
        }
    }

    @Test
    fun `redelegate src and dst at correct field positions`() {
        val payload =
            CosmosStakingPayload.Redelegate(
                validatorSrcAddress = FX.VALIDATOR_A,
                validatorDstAddress = FX.VALIDATOR_B,
                denom = "uluna",
                amount = "100000",
            )
        val a = resolve(payload)
        val swapped =
            resolve(
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = FX.VALIDATOR_B,
                    validatorDstAddress = FX.VALIDATOR_A,
                    denom = "uluna",
                    amount = "100000",
                )
            )
        // Swapping src and dst must produce different SignDoc bytes — pinning the field 2 / field 3
        // contract end-to-end through the resolver.
        assertNotEquals(a.bodyBytes, swapped.bodyBytes)
    }

    // MARK: - WithdrawRewards

    @Test
    fun `withdrawRewards rejects empty validator list`() {
        val payload =
            CosmosStakingPayload.WithdrawRewards(validators = emptyList(), denom = "uluna")
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.NoValidatorsToClaim> {
            resolve(payload)
        }
    }

    @Test
    fun `withdrawRewards single-validator collapses to one msg in TxBody`() {
        val payload =
            CosmosStakingPayload.WithdrawRewards(
                validators = listOf(FX.VALIDATOR_A),
                denom = "uluna",
            )
        val result = resolve(payload)
        val expected =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeWithdrawDelegatorReward(
                        delegator = FX.DELEGATOR,
                        validator = FX.VALIDATOR_A,
                    )
                )
            )
        assertContentEquals(expected, Base64.getDecoder().decode(result.bodyBytes))
    }

    @Test
    fun `withdrawRewards N-validator batch packs N msgs in TxBody`() {
        val payload =
            CosmosStakingPayload.WithdrawRewards(
                validators = listOf(FX.VALIDATOR_A, FX.VALIDATOR_B, FX.VALIDATOR_C),
                denom = "uluna",
            )
        val result = resolve(payload)
        val expected =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(FX.VALIDATOR_A, FX.VALIDATOR_B, FX.VALIDATOR_C).map { validator ->
                    CosmosStakingHelper.encodeWithdrawDelegatorReward(
                        delegator = FX.DELEGATOR,
                        validator = validator,
                    )
                }
            )
        assertContentEquals(expected, Base64.getDecoder().decode(result.bodyBytes))
    }

    @Test
    fun `withdrawRewards rejects batches over the 8-validator cap`() {
        // 9 validators — exceeds MAX_BATCH_WITHDRAW_VALIDATORS = 8.
        val nine =
            (0 until 9).map { i ->
                Bech32TestEncoder.encode("terravaloper", ByteArray(20) { (i * 10 + it).toByte() })
            }
        val payload = CosmosStakingPayload.WithdrawRewards(validators = nine, denom = "uluna")
        val ex =
            assertFailsWith<
                CosmosStakingSignDataResolver.ResolverException.TooManyValidatorsToClaim
            > {
                resolve(payload)
            }
        assertEquals(8, ex.max)
        assertEquals(9, ex.actual)
    }

    @Test
    fun `withdrawRewards accepts exactly 8 validators at the cap boundary`() {
        val eight =
            (0 until 8).map { i ->
                Bech32TestEncoder.encode("terravaloper", ByteArray(20) { (i * 10 + it).toByte() })
            }
        val payload = CosmosStakingPayload.WithdrawRewards(validators = eight, denom = "uluna")
        // Must not throw.
        resolve(payload)
    }

    // MARK: - Gas + fee scaling

    @Test
    fun `single-msg flow uses base Terra gas + fee`() {
        // Sanity: by re-encoding AuthInfo with the same base values, the bytes should match.
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        val result = resolve(payload)
        val expectedAuth =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = ByteArray(33) { 0x02 },
                sequence = FX.SEQUENCE,
                gasLimit = 300_000L,
                feeDenom = "uluna",
                feeAmount = 7_500L,
            )
        assertContentEquals(expectedAuth, Base64.getDecoder().decode(result.authInfoBytes))
    }

    @Test
    fun `LUNC config produces columbus-5 chainId and LUNC gas`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        val result =
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = Chain.TerraClassic,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = FX.PUBKEY_HEX,
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        assertEquals("columbus-5", result.chainId)
        val expectedAuth =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = ByteArray(33) { 0x02 },
                sequence = FX.SEQUENCE,
                gasLimit = 1_500_000L,
                feeDenom = "uluna",
                feeAmount = 100_000_000L,
            )
        assertContentEquals(expectedAuth, Base64.getDecoder().decode(result.authInfoBytes))
    }

    @Test
    fun `batched claim scales gas + fee linearly with validator count`() {
        val payload =
            CosmosStakingPayload.WithdrawRewards(
                validators = listOf(FX.VALIDATOR_A, FX.VALIDATOR_B, FX.VALIDATOR_C),
                denom = "uluna",
            )
        val result = resolve(payload)
        val expectedAuth =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = ByteArray(33) { 0x02 },
                sequence = FX.SEQUENCE,
                // 3 msgs × 300_000 base = 900_000
                gasLimit = 900_000L,
                feeDenom = "uluna",
                // 3 msgs × 7_500 base = 22_500
                feeAmount = 22_500L,
            )
        assertContentEquals(expectedAuth, Base64.getDecoder().decode(result.authInfoBytes))
    }

    // MARK: - PubKey + chainSpecific guards

    @Test
    fun `rejects non-Cosmos blockchain specific`() {
        val ethereumSpecific =
            BlockChainSpecific.UTXO(byteFee = BigInteger.valueOf(10), sendMaxAmount = false)
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.MissingChainSpecific> {
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = Chain.Terra,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = FX.PUBKEY_HEX,
                chainSpecific = ethereumSpecific,
            )
        }
    }

    @Test
    fun `rejects uncompressed pubkey`() {
        // 65-byte uncompressed key (0x04 prefix) — must be rejected.
        val uncompressed = "04" + "00".repeat(64)
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.InvalidPublicKey> {
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = Chain.Terra,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = uncompressed,
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        }
    }

    @Test
    fun `rejects malformed hex pubkey`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = FX.VALIDATOR_A,
                denom = "uluna",
                amount = "1000000",
            )
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.InvalidPublicKey> {
            CosmosStakingSignDataResolver.resolve(
                payload = payload,
                chain = Chain.Terra,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = "not-hex",
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        }
    }

    private fun resolve(payload: CosmosStakingPayload) =
        CosmosStakingSignDataResolver.resolve(
            payload = payload,
            chain = Chain.Terra,
            delegatorAddress = FX.DELEGATOR,
            hexPublicKey = FX.PUBKEY_HEX,
            chainSpecific = FX.COSMOS_SPECIFIC,
        )
}
