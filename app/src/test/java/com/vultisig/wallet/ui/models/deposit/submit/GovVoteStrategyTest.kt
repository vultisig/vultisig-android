@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.blockchain.cosmos.gov.CosmosGovVote
import com.vultisig.wallet.data.blockchain.cosmos.gov.CosmosVoteOption
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import vultisig.keysign.v1.TransactionType

internal class GovVoteStrategyTest {

    private val proposalId = TextFieldState()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private var state = DepositFormUiModel(selectedVoteOption = CosmosVoteOption.NO_WITH_VETO)

    @Test
    fun `vote is a zero-value signDirect payload addressed to the voter with no memo`() = runTest {
        givenSpecific()
        proposalId.setTextAndPlaceCursorAtEnd("312")

        val tx = build().build()

        assertEquals(CosmosGovVote(312uL, CosmosVoteOption.NO_WITH_VETO), tx.governanceVote)
        assertEquals("", tx.memo)
        assertEquals(BigInteger.ZERO, tx.srcTokenValue.value)
        assertEquals(VOTER, tx.srcAddress)
        assertEquals(VOTER, tx.dstAddress)
        val signDirect = assertNotNull(tx.signDirect)
        assertEquals("dydx-mainnet-1", signDirect.chainId)
        assertEquals("9", signDirect.accountNumber)
        val cosmos = tx.blockChainSpecific as BlockChainSpecific.Cosmos
        assertEquals(TransactionType.TRANSACTION_TYPE_UNSPECIFIED, cosmos.transactionType)

        val body = ProtoBuf.decodeFromByteArray<TestTxBody>(decode(signDirect.bodyBytes))
        assertEquals("", body.memo)
        val any = body.messages.single()
        assertEquals("/cosmos.gov.v1beta1.MsgVote", any.typeUrl)
        val msg = ProtoBuf.decodeFromByteArray<TestMsgVote>(any.value)
        assertEquals(312L, msg.proposalId)
        assertEquals(VOTER, msg.voter)
        assertEquals(CosmosVoteOption.NO_WITH_VETO.protoValue, msg.option)

        val fee =
            assertNotNull(
                ProtoBuf.decodeFromByteArray<TestAuthInfo>(decode(signDirect.authInfoBytes)).fee
            )
        assertEquals(listOf(TestCoin("adydx", FEE.toString())), fee.amount)
        assertEquals(CosmosHelper.getChainGasLimit(Chain.Dydx), fee.gasLimit)
    }

    @Test
    fun `no ballot is refused`() = runTest {
        givenSpecific()
        state = state.copy(selectedVoteOption = null)
        proposalId.setTextAndPlaceCursorAtEnd("1")

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0", "-3", "1.5", "18446744073709551616"])
    fun `an invalid proposal id is refused`(text: String) = runTest {
        givenSpecific()
        proposalId.setTextAndPlaceCursorAtEnd(text)

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    private fun build() =
        GovVoteStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { Chain.Dydx },
            stateProvider = { state },
            selectedTokenProvider = { voter },
            proposalIdFieldState = proposalId,
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, token, _ -> TokenValue(FEE, token) },
            getFeesFiatValue = { _, _, _ ->
                EstimatedGasFee(
                    formattedFiatValue = "$0.01",
                    formattedTokenValue = "0.0025",
                    tokenValue = TokenValue(FEE, voter),
                    fiatValue = mockk(relaxed = true),
                )
            },
            cosmosChainId = { "dydx-mainnet-1" },
        )

    private fun givenSpecific() {
        coEvery {
            specificRepo.getSpecific(
                chain = Chain.Dydx,
                address = VOTER,
                token = any(),
                gasFee = any(),
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.valueOf(9),
                    sequence = BigInteger.valueOf(3),
                    gas = FEE,
                    ibcDenomTraces = null,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                )
            )
    }

    private val voter =
        Coin(
            chain = Chain.Dydx,
            ticker = "DYDX",
            logo = "",
            address = VOTER,
            decimal = 18,
            hexPublicKey = "02" + "22".repeat(32),
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun decode(base64: String) = Base64.getDecoder().decode(base64)

    @Serializable
    private class TestAny(
        @ProtoNumber(1) val typeUrl: String = "",
        @ProtoNumber(2) val value: ByteArray = ByteArray(0),
    )

    @Serializable
    private class TestTxBody(
        @ProtoNumber(1) val messages: List<TestAny> = emptyList(),
        @ProtoNumber(2) val memo: String = "",
    )

    @Serializable
    private class TestMsgVote(
        @ProtoNumber(1) val proposalId: Long = 0,
        @ProtoNumber(2) val voter: String = "",
        @ProtoNumber(3) val option: Long = 0,
    )

    @Serializable
    private data class TestCoin(
        @ProtoNumber(1) val denom: String = "",
        @ProtoNumber(2) val amount: String = "",
    )

    @Serializable
    private class TestFee(
        @ProtoNumber(1) val amount: List<TestCoin> = emptyList(),
        @ProtoNumber(2) val gasLimit: Long = 0,
    )

    @Serializable private class TestAuthInfo(@ProtoNumber(2) val fee: TestFee? = null)

    private companion object {
        const val VOTER = "dydx1voter0000000000000000000000000000000"
        val FEE = BigInteger("2500000000000000")
    }
}
