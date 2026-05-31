package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * Locks down the bridge that produces a complete
 * [com.vultisig.wallet.data.models.payload .KeysignPayload] with `signDirect` wired for a Cosmos
 * staking operation. Tests cover field mapping for all four op-types (delegate / undelegate /
 * redelegate / withdrawRewards) and confirm the SignDoc bytes round-trip through the resolver.
 */
class BuildCosmosStakingKeysignPayloadUseCaseTests {

    private val useCase = BuildCosmosStakingKeysignPayloadUseCaseImpl()

    private fun coin(chain: Chain = Chain.Terra) =
        Coin(
            chain = chain,
            ticker = "LUNA",
            logo = "",
            address = "terra1delegator00000000000000000000000000ab",
            decimal = 6,
            hexPublicKey = "020202020202020202020202020202020202020202020202020202020202020202",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private val cosmosSpecific =
        BlockChainSpecific.Cosmos(
            accountNumber = BigInteger.valueOf(100),
            sequence = BigInteger.valueOf(42),
            gas = BigInteger.ZERO,
            ibcDenomTraces = null,
            transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
        )

    private val validatorA = Bech32TestEncoder.encode("terravaloper", ByteArray(20) { it.toByte() })
    private val validatorB =
        Bech32TestEncoder.encode("terravaloper", ByteArray(20) { (it + 10).toByte() })

    @Test
    fun `delegate produces KeysignPayload with signDirect populated`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = validatorA,
                denom = "uluna",
                amount = "1000000",
            )
        val result =
            useCase(
                coin = coin(),
                payload = payload,
                blockChainSpecific = cosmosSpecific,
                vaultPublicKeyECDSA = "pubkey-ecdsa",
                vaultLocalPartyID = "local-party",
                libType = SigningLibType.DKLS,
            )
        assertEquals(coin(), result.coin)
        assertEquals(validatorA, result.toAddress)
        assertEquals(BigInteger.valueOf(1_000_000L), result.toAmount)
        assertEquals(cosmosSpecific, result.blockChainSpecific)
        assertNull(result.memo)
        assertEquals("pubkey-ecdsa", result.vaultPublicKeyECDSA)
        assertEquals("local-party", result.vaultLocalPartyID)
        assertEquals(SigningLibType.DKLS, result.libType)
        assertNull(result.wasmExecuteContractPayload)

        val signDirect = assertNotNull(result.signDirect)
        assertEquals("phoenix-1", signDirect.chainId)
        assertEquals("100", signDirect.accountNumber)
        val expectedBody =
            CosmosStakingHelper.buildTxBodyMulti(
                listOf(
                    CosmosStakingHelper.encodeDelegate(
                        delegator = coin().address,
                        validator = validatorA,
                        amount = "1000000",
                        denom = "uluna",
                    )
                )
            )
        assertContentEquals(expectedBody, Base64.getDecoder().decode(signDirect.bodyBytes))
    }

    @Test
    fun `undelegate sets toAddress to validator and toAmount to amount`() {
        val payload =
            CosmosStakingPayload.Undelegate(
                validatorAddress = validatorA,
                denom = "uluna",
                amount = "500000",
            )
        val result =
            useCase(
                coin = coin(),
                payload = payload,
                blockChainSpecific = cosmosSpecific,
                vaultPublicKeyECDSA = "p",
                vaultLocalPartyID = "l",
                libType = SigningLibType.DKLS,
            )
        assertEquals(validatorA, result.toAddress)
        assertEquals(BigInteger.valueOf(500_000L), result.toAmount)
    }

    @Test
    fun `redelegate sets toAddress to dst validator`() {
        val payload =
            CosmosStakingPayload.Redelegate(
                validatorSrcAddress = validatorA,
                validatorDstAddress = validatorB,
                denom = "uluna",
                amount = "100000",
            )
        val result =
            useCase(
                coin = coin(),
                payload = payload,
                blockChainSpecific = cosmosSpecific,
                vaultPublicKeyECDSA = "p",
                vaultLocalPartyID = "l",
                libType = SigningLibType.DKLS,
            )
        // Verify-screen display: the user is moving stake TO the destination validator.
        assertEquals(validatorB, result.toAddress)
        assertEquals(BigInteger.valueOf(100_000L), result.toAmount)
    }

    @Test
    fun `withdrawRewards toAmount is zero and toAddress is first validator`() {
        // MsgWithdrawDelegatorReward carries no Coin field — toAmount = 0. The first validator is
        // a best-effort display value for the verify screen; the SignDirect bytes encode the full
        // list.
        val payload =
            CosmosStakingPayload.WithdrawRewards(
                validators = listOf(validatorA, validatorB),
                denom = "uluna",
            )
        val result =
            useCase(
                coin = coin(),
                payload = payload,
                blockChainSpecific = cosmosSpecific,
                vaultPublicKeyECDSA = "p",
                vaultLocalPartyID = "l",
                libType = SigningLibType.DKLS,
            )
        assertEquals(BigInteger.ZERO, result.toAmount)
        assertEquals(validatorA, result.toAddress)
    }

    @Test
    fun `LUNC coin produces columbus-5 chainId in signDirect`() {
        val payload =
            CosmosStakingPayload.Delegate(
                validatorAddress = validatorA,
                denom = "uluna",
                amount = "1000000",
            )
        val result =
            useCase(
                coin = coin(Chain.TerraClassic),
                payload = payload,
                blockChainSpecific = cosmosSpecific,
                vaultPublicKeyECDSA = "p",
                vaultLocalPartyID = "l",
                libType = SigningLibType.DKLS,
            )
        assertEquals("columbus-5", result.signDirect?.chainId)
    }
}
