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
 * Locks down the ML-DSA staking-resolver contract for QBTC. Mirrors iOS
 * `QBTCStakingSignDataResolverTests.swift` (vultisig-ios PR #4481).
 *
 * QBTC signs with ML-DSA (post-quantum), so [CosmosStakingSignDataResolver.resolve] skips the
 * secp256k1 33-byte pubkey guard and stamps `/cosmos.crypto.mldsa.PubKey` into AuthInfo. The msg
 * bodies are pubkey-agnostic, so they must still byte-match the shared [CosmosStakingHelper]
 * encoders. Each test asserts behaviour at the resolver boundary; the secp256k1 `resolve` path is
 * covered by [CosmosStakingSignDataResolverTests] and pinned here as a regression where it matters.
 */
class QbtcStakingSignDataResolverTests {

    private object FX {
        const val DELEGATOR = "qbtc1delegator0000000000000000000000000000"
        const val ACCOUNT_NUMBER = 100L
        const val SEQUENCE = 42L
        const val DENOM = "qbtc"
        // ~1312-byte ML-DSA-44 pubkey — far larger than secp256k1's 33 bytes. A deterministic
        // all-0xAB fill keeps the AuthInfo bytes stable across runs.
        val MLDSA_PUBKEY_HEX = "ab".repeat(1312)
        val MLDSA_PUBKEY: ByteArray = ByteArray(1312) { 0xAB.toByte() }
        const val MLDSA_PUBKEY_TYPE_URL = "/cosmos.crypto.mldsa.PubKey"

        val VALIDATOR_A = makeValoper(seedByte = 0x0A)
        val VALIDATOR_B = makeValoper(seedByte = 0x0B)
        val VALIDATOR_C = makeValoper(seedByte = 0x0C)

        private fun makeValoper(seedByte: Int): String {
            val payload = ByteArray(20) { ((seedByte + it) and 0xFF).toByte() }
            return Bech32TestEncoder.encode("qbtcvaloper", payload)
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

    // MARK: - Bodies match the shared, pubkey-agnostic encoders

    @Test
    fun `delegate body matches the shared encoder and carries qbtc-testnet chainId`() {
        val result = resolve(CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"))
        assertEquals("qbtc-testnet", result.chainId)
        assertEquals(FX.ACCOUNT_NUMBER.toString(), result.accountNumber)
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeDelegate(
                        delegator = FX.DELEGATOR,
                        validator = FX.VALIDATOR_A,
                        amount = "100000000",
                        denom = FX.DENOM,
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(result.bodyBytes))
    }

    @Test
    fun `undelegate body matches the shared encoder`() {
        val result = resolve(CosmosStakingPayload.Undelegate(FX.VALIDATOR_A, amount = "50000000"))
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeUndelegate(
                        delegator = FX.DELEGATOR,
                        validator = FX.VALIDATOR_A,
                        amount = "50000000",
                        denom = FX.DENOM,
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(result.bodyBytes))
    }

    @Test
    fun `redelegate body matches the shared encoder`() {
        val result =
            resolve(
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = FX.VALIDATOR_A,
                    validatorDstAddress = FX.VALIDATOR_B,
                    amount = "10000000",
                )
            )
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeBeginRedelegate(
                        delegator = FX.DELEGATOR,
                        validatorSrc = FX.VALIDATOR_A,
                        validatorDst = FX.VALIDATOR_B,
                        amount = "10000000",
                        denom = FX.DENOM,
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(result.bodyBytes))
    }

    @Test
    fun `withdrawRewards body matches the shared encoder`() {
        val result =
            resolve(CosmosStakingPayload.WithdrawRewards(validators = listOf(FX.VALIDATOR_A)))
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeWithdrawDelegatorReward(
                        delegator = FX.DELEGATOR,
                        validator = FX.VALIDATOR_A,
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(result.bodyBytes))
    }

    // MARK: - AuthInfo carries the ML-DSA pubkey + QBTC gas/fee

    @Test
    fun `AuthInfo stamps the ML-DSA pubkey URL and QBTC base gas + fee`() {
        val result = resolve(CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"))
        val expectedAuth =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = FX.MLDSA_PUBKEY,
                sequence = FX.SEQUENCE,
                gasLimit = 400_000L,
                feeDenom = FX.DENOM,
                feeAmount = 800L,
                pubKeyTypeUrl = FX.MLDSA_PUBKEY_TYPE_URL,
            )
        assertContentEquals(expectedAuth, Base64.getDecoder().decode(result.authInfoBytes))
    }

    @Test
    fun `batched claim scales gas + fee linearly with validator count`() {
        val result =
            resolve(
                CosmosStakingPayload.WithdrawRewards(
                    validators = listOf(FX.VALIDATOR_A, FX.VALIDATOR_B, FX.VALIDATOR_C)
                )
            )
        val expectedAuth =
            CosmosStakingHelper.buildAuthInfo(
                pubKey = FX.MLDSA_PUBKEY,
                sequence = FX.SEQUENCE,
                gasLimit = 1_200_000L, // 3 × 400_000
                feeDenom = FX.DENOM,
                feeAmount = 2_400L, // 3 × 800
                pubKeyTypeUrl = FX.MLDSA_PUBKEY_TYPE_URL,
            )
        assertContentEquals(expectedAuth, Base64.getDecoder().decode(result.authInfoBytes))
    }

    @Test
    fun `ML-DSA resolve is deterministic across repeated calls`() {
        val payload = CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000")
        assertEquals(resolve(payload), resolve(payload))
    }

    // MARK: - PubKey guard divergence (the core of the ML-DSA path)

    @Test
    fun `ML-DSA resolve accepts a 1312-byte ML-DSA pubkey that the secp256k1 guard would reject`() {
        // Must not throw — the secp256k1 33-byte guard is skipped on the ML-DSA path.
        resolve(CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"))
    }

    @Test
    fun `secp256k1 resolve still rejects the ML-DSA pubkey (guard unchanged)`() {
        // Regression: the secp256k1 path must keep enforcing the 33-byte compressed-key guard, so
        // an ML-DSA-sized key fed to `resolve` is rejected up-front.
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.InvalidPublicKey> {
            CosmosStakingSignDataResolver.resolve(
                payload = CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"),
                chain = Chain.Terra,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = FX.MLDSA_PUBKEY_HEX,
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        }
    }

    @Test
    fun `ML-DSA resolve rejects an empty pubkey`() {
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.InvalidPublicKey> {
            CosmosStakingSignDataResolver.resolve(
                payload = CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"),
                chain = Chain.Qbtc,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = "",
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        }
    }

    @Test
    fun `ML-DSA resolve rejects a malformed hex pubkey`() {
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.InvalidPublicKey> {
            CosmosStakingSignDataResolver.resolve(
                payload = CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"),
                chain = Chain.Qbtc,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = "not-hex",
                chainSpecific = FX.COSMOS_SPECIFIC,
            )
        }
    }

    // MARK: - Preflight + payload guards (shared with the secp256k1 path)

    @Test
    fun `ML-DSA resolve preflights validators against the QBTC valoper HRP`() {
        // A terravaloper address has the right structural shape but the wrong HRP for QBTC.
        val terra = Bech32TestEncoder.encode("terravaloper", ByteArray(20) { it.toByte() })
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.ValidatorPreflightFailed> {
            resolve(CosmosStakingPayload.Delegate(validatorAddress = terra, amount = "100000000"))
        }
    }

    @Test
    fun `ML-DSA resolve rejects self-redelegation`() {
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.SelfRedelegation> {
            resolve(
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = FX.VALIDATOR_A,
                    validatorDstAddress = FX.VALIDATOR_A,
                    amount = "10000000",
                )
            )
        }
    }

    @Test
    fun `ML-DSA resolve rejects a non-positive amount`() {
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.InvalidAmount> {
            resolve(CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "0"))
        }
    }

    @Test
    fun `ML-DSA resolve rejects a non-Cosmos blockchain specific`() {
        val utxo = BlockChainSpecific.UTXO(byteFee = BigInteger.valueOf(10), sendMaxAmount = false)
        assertFailsWith<CosmosStakingSignDataResolver.ResolverException.MissingChainSpecific> {
            CosmosStakingSignDataResolver.resolve(
                payload = CosmosStakingPayload.Delegate(FX.VALIDATOR_A, amount = "100000000"),
                chain = Chain.Qbtc,
                delegatorAddress = FX.DELEGATOR,
                hexPublicKey = FX.MLDSA_PUBKEY_HEX,
                chainSpecific = utxo,
            )
        }
    }

    @Test
    fun `redelegate swapping src and dst produces different bytes`() {
        val normal =
            resolve(
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = FX.VALIDATOR_A,
                    validatorDstAddress = FX.VALIDATOR_B,
                    amount = "10000000",
                )
            )
        val swapped =
            resolve(
                CosmosStakingPayload.Redelegate(
                    validatorSrcAddress = FX.VALIDATOR_B,
                    validatorDstAddress = FX.VALIDATOR_A,
                    amount = "10000000",
                )
            )
        assertNotEquals(normal.bodyBytes, swapped.bodyBytes)
    }

    private fun resolve(payload: CosmosStakingPayload) =
        CosmosStakingSignDataResolver.resolve(
            payload = payload,
            chain = Chain.Qbtc,
            delegatorAddress = FX.DELEGATOR,
            hexPublicKey = FX.MLDSA_PUBKEY_HEX,
            chainSpecific = FX.COSMOS_SPECIFIC,
        )
}
