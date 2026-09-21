package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * Pins `Erc20ApprovePayload.reset_allowance_first` across both proto mappers. Every co-signer
 * rebuilds its message list from the wire payload, so a mapper that dropped the flag would sign one
 * approve leg against the initiator's two and stall the ceremony; one that invented it would do the
 * reverse.
 */
class KeysignPayloadProtoMapperErc20ApproveTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `the reset flag survives the domain to proto to domain round-trip`() {
        val proto = requireNotNull(outbound(payload(resetAllowanceFirst = true)))

        proto.erc20ApprovePayload?.resetAllowanceFirst shouldBe true

        inbound(proto).approvePayload shouldBe
            ERC20ApprovePayload(amount = AMOUNT, spender = SPENDER, resetAllowanceFirst = true)
    }

    // A payload from a sender that never sets the field must keep today's single-approve shape.
    @Test
    fun `an approve without the flag round-trips as a plain approve`() {
        val proto = requireNotNull(outbound(payload(resetAllowanceFirst = false)))

        proto.erc20ApprovePayload?.resetAllowanceFirst shouldBe false

        inbound(proto).approvePayload shouldBe
            ERC20ApprovePayload(amount = AMOUNT, spender = SPENDER)
    }

    private fun payload(resetAllowanceFirst: Boolean) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "USDT",
                    logo = "usdt",
                    address = "0x1234567890123456789012345678901234567890",
                    decimal = 6,
                    hexPublicKey = "pub",
                    priceProviderID = "tether",
                    contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
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
            approvePayload =
                ERC20ApprovePayload(
                    amount = AMOUNT,
                    spender = SPENDER,
                    resetAllowanceFirst = resetAllowanceFirst,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val SPENDER = "0x111111125421ca6dc452d289314280a0f8842a65"
        val AMOUNT: BigInteger = BigInteger.valueOf(5_000_000)
    }
}
