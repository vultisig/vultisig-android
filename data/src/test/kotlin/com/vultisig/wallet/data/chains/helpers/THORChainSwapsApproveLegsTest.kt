package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Ethereum

/**
 * Pins the approve legs every signer builds from a shared [ERC20ApprovePayload]: their count, the
 * amount each sets and the nonce each consumes are the cross-device contract with the SDK's
 * `getErc20ApproveSigningInputs` (vultisig-sdk `approveReset.test.ts`, payload nonce 7). A signer
 * that derived a different list would hash a different message set and stall the ceremony.
 *
 * The fixture is a Sei token so the chain id resolves without wallet-core's native library, which
 * JVM tests cannot load; the leg logic is chain-agnostic.
 */
class THORChainSwapsApproveLegsTest {

    private val swaps = THORChainSwaps(ecdsaKey = "", ecdsaChainCode = "", eddsaKey = "")

    @Test
    fun `a payload without the reset flag keeps the single approve at the payload nonce`() {
        val legs = swaps.getPreSignedApproveInputData(approve(resetAllowanceFirst = false), payload)

        legs.map(::describe) shouldBe listOf(Leg(nonce = 7, amount = AMOUNT))
    }

    @Test
    fun `a payload with the reset flag emits approve(0) then approve(amount) on consecutive nonces`() {
        val legs = swaps.getPreSignedApproveInputData(approve(resetAllowanceFirst = true), payload)

        legs.map(::describe) shouldBe
            listOf(Leg(nonce = 7, amount = BigInteger.ZERO), Leg(nonce = 8, amount = AMOUNT))
    }

    // Both legs address the same spender on the same token; the reset is not a different approval.
    @Test
    fun `both legs target the token contract and the same spender`() {
        val legs =
            swaps.getPreSignedApproveInputData(approve(resetAllowanceFirst = true), payload).map {
                Ethereum.SigningInput.parseFrom(it)
            }

        legs.map { it.toAddress }.toSet() shouldBe setOf(USDT)
        legs.map { it.transaction.erc20Approve.spender }.toSet() shouldBe setOf(SPENDER)
    }

    // The dependent transaction starts after the last approve leg, so the swap's nonce offset is
    // the leg count — two with the reset, one without.
    @Test
    fun `the leg amounts follow the reset flag`() {
        approve(resetAllowanceFirst = false).legAmounts shouldBe listOf(AMOUNT)
        approve(resetAllowanceFirst = true).legAmounts shouldBe listOf(BigInteger.ZERO, AMOUNT)
    }

    private data class Leg(val nonce: Int, val amount: BigInteger)

    private fun describe(inputData: ByteArray): Leg {
        val input = Ethereum.SigningInput.parseFrom(inputData)
        return Leg(
            nonce = BigInteger(1, input.nonce.toByteArray()).toInt(),
            amount = BigInteger(1, input.transaction.erc20Approve.amount.toByteArray()),
        )
    }

    private fun approve(resetAllowanceFirst: Boolean) =
        ERC20ApprovePayload(
            amount = AMOUNT,
            spender = SPENDER,
            resetAllowanceFirst = resetAllowanceFirst,
        )

    private val payload =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Sei,
                    ticker = "USDT",
                    logo = "usdt",
                    address = "0x1234567890123456789012345678901234567890",
                    decimal = 6,
                    hexPublicKey = "",
                    priceProviderID = "tether",
                    contractAddress = USDT,
                    isNativeToken = false,
                ),
            toAddress = SPENDER,
            toAmount = AMOUNT,
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger("1000000000"),
                    priorityFeeWei = BigInteger("100000000"),
                    nonce = BigInteger.valueOf(7),
                    gasLimit = BigInteger.valueOf(210_000),
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val SPENDER = "0x111111125421ca6dc452d289314280a0f8842a65"
        val AMOUNT: BigInteger = BigInteger.valueOf(5_000_000)
    }
}
