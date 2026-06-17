package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.Fee
import com.vultisig.wallet.data.usecases.Message
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * Covers the joiner-side classification a device recovers from the transmitted SignDoc when it has
 * no local DepositTransactionRepository entry (issue #4939): a Cosmos staking / distribution
 * message marks the payload as a deposit, while a plain send / dApp transaction does not.
 */
internal class IsCosmosStakingDepositTest {

    private val lunaCoin =
        Coin(
            chain = Chain.TerraClassic,
            ticker = "LUNC",
            logo = "lunc",
            address = "terra1pxpx9",
            decimal = 6,
            hexPublicKey = "hex",
            priceProviderID = "terra-luna",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `delegate message is a staking deposit`() {
        val payload = cosmosPayload(signDirect = signDirect())
        val parse = parseReturning(CosmosStakingHelper.MSG_DELEGATE_TYPE_URL)

        payload.isCosmosStakingDeposit(parse) shouldBe true
    }

    @Test
    fun `withdraw-reward (claim) message is a staking deposit`() {
        val payload = cosmosPayload(signDirect = signDirect())
        val parse = parseReturning(CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL)

        payload.isCosmosStakingDeposit(parse) shouldBe true
    }

    @Test
    fun `plain MsgSend is not a staking deposit`() {
        val payload = cosmosPayload(signDirect = signDirect())
        val parse = parseReturning("/cosmos.bank.v1beta1.MsgSend")

        payload.isCosmosStakingDeposit(parse) shouldBe false
    }

    @Test
    fun `no signDirect is not a staking deposit and never parses`() {
        val payload = cosmosPayload(signDirect = null)
        val parse = ParseCosmosMessageUseCase {
            error("should not be called when signDirect is null")
        }

        payload.isCosmosStakingDeposit(parse) shouldBe false
    }

    @Test
    fun `unparseable signDirect is not a staking deposit`() {
        val payload = cosmosPayload(signDirect = signDirect())
        val parse = ParseCosmosMessageUseCase { throw IllegalArgumentException("malformed body") }

        payload.isCosmosStakingDeposit(parse) shouldBe false
    }

    private fun parseReturning(vararg typeUrls: String) = ParseCosmosMessageUseCase {
        CosmosMessage(
            chainId = "columbus-5",
            accountNumber = "1",
            sequence = "0",
            memo = "",
            messages = typeUrls.map { Message(typeUrl = it, value = JsonNull) },
            authInfoFee = Fee(amount = emptyList()),
        )
    }

    private fun signDirect() =
        SignDirectProto(
            bodyBytes = "",
            authInfoBytes = "",
            chainId = "columbus-5",
            accountNumber = "1",
        )

    private fun cosmosPayload(signDirect: SignDirectProto?) =
        KeysignPayload(
            coin = lunaCoin,
            toAddress = "terra1validator",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.ONE,
                    sequence = BigInteger.ZERO,
                    gas = BigInteger.valueOf(200_000),
                    ibcDenomTraces = null,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
            signDirect = signDirect,
        )
}
