@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_UNBOND
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.ui.models.deposit.BondAssetsState
import com.vultisig.wallet.ui.models.deposit.BondedRuneCeiling
import com.vultisig.wallet.ui.models.deposit.BondedUnitsCeiling
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.bondedRuneAmountForNode
import com.vultisig.wallet.ui.models.deposit.unbondRuneCeiling
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class UnbondStrategyTest {

    private val nodeAddress = TextFieldState()
    private val tokenAmount = TextFieldState()
    private val provider = TextFieldState()
    private val assets = TextFieldState()
    private val lpUnits = TextFieldState()

    private val chainRepo: ChainAccountAddressRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val assetsValidator: DepositMemoAssetsValidatorUseCase = mockk()

    @Test
    fun `Thor unbond memo encodes amount and srcTokenValue is zero`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0.5")

        val tx = build(Chain.ThorChain, state = thorUnbondState()).build()

        assertEquals("UNBOND:thorNode:50000000", tx.memo)
        assertEquals(BigInteger.ZERO, tx.srcTokenValue.value)
        assertEquals(OPERATION_UNBOND, tx.operation)
    }

    @Test
    fun `Maya unbond memo encodes assets and lpUnits and srcTokenValue is one`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        every { assetsValidator.invoke("MAYA.CACAO") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")
        assets.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        lpUnits.setTextAndPlaceCursorAtEnd("1000")

        val tx =
            build(
                    Chain.MayaChain,
                    selectedTokenChain = Chain.MayaChain,
                    state = mayaUnbondState(units = "1000"),
                )
                .build()

        assertEquals("UNBOND:MAYA.CACAO:1000:mayaNode", tx.memo)
        assertEquals(BigInteger.ONE, tx.srcTokenValue.value)
    }

    @Test
    fun `Maya unbond builds when the entered units equal the bonded ceiling`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        every { assetsValidator.invoke("MAYA.CACAO") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")
        assets.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        lpUnits.setTextAndPlaceCursorAtEnd("1000")

        val tx =
            build(
                    Chain.MayaChain,
                    selectedTokenChain = Chain.MayaChain,
                    state = mayaUnbondState(units = "1000"),
                )
                .build()

        tx.memo shouldBe "UNBOND:MAYA.CACAO:1000:mayaNode"
    }

    @Test
    fun `Maya unbond throws when the entered units exceed what is bonded to the node`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        every { assetsValidator.invoke("MAYA.CACAO") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")
        assets.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        lpUnits.setTextAndPlaceCursorAtEnd("1001")

        shouldThrow<InvalidTransactionDataException> {
            build(
                    Chain.MayaChain,
                    selectedTokenChain = Chain.MayaChain,
                    state = mayaUnbondState(units = "1000"),
                )
                .build()
        }
    }

    @Test
    fun `Maya unbond throws when the ceiling was measured on a different node`() = runTest {
        // The address field can be re-typed after the units were fetched, and the memo is built
        // from the field: a ceiling from the previous node must not authorise this one.
        coEvery { chainRepo.isValid(Chain.MayaChain, "otherNode") } returns true
        every { assetsValidator.invoke("MAYA.CACAO") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("otherNode")
        assets.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        lpUnits.setTextAndPlaceCursorAtEnd("500")

        shouldThrow<InvalidTransactionDataException> {
            build(
                    Chain.MayaChain,
                    selectedTokenChain = Chain.MayaChain,
                    state = mayaUnbondState(units = "1000"),
                )
                .build()
        }
    }

    @Test
    fun `Maya unbond throws when the ceiling belongs to another pool`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        every { assetsValidator.invoke("ETH.ETH") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")
        assets.setTextAndPlaceCursorAtEnd("ETH.ETH")
        lpUnits.setTextAndPlaceCursorAtEnd("500")

        shouldThrow<InvalidTransactionDataException> {
            build(
                    Chain.MayaChain,
                    selectedTokenChain = Chain.MayaChain,
                    state = mayaUnbondState(units = "1000"),
                )
                .build()
        }
    }

    @Test
    fun `Maya unbond throws when no bonded position was loaded`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        every { assetsValidator.invoke("MAYA.CACAO") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")
        assets.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        lpUnits.setTextAndPlaceCursorAtEnd("1000")

        shouldThrow<InvalidTransactionDataException> {
            build(Chain.MayaChain, selectedTokenChain = Chain.MayaChain).build()
        }
    }

    private fun mayaUnbondState(
        units: String,
        node: String = "mayaNode",
        asset: String = "MAYA.CACAO",
    ) =
        DepositFormUiModel(
            depositChain = Chain.MayaChain,
            depositOption = DepositOption.Unbond,
            selectedBondAsset = asset,
            bondAssetsState =
                BondAssetsState.Loaded(
                    BondedUnitsCeiling(nodeAddress = node, asset = asset, units = units)
                ),
        )

    @Test
    fun `Thor unbond throws when amount is zero`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0")

        assertFailsWith<InvalidTransactionDataException> { build(Chain.ThorChain).build() }
    }

    @Test
    fun `Thor unbond builds when the entered amount equals the node bond`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0.5")

        val tx = build(Chain.ThorChain, state = thorUnbondState()).build()

        tx.memo shouldBe "UNBOND:thorNode:50000000"
    }

    @Test
    fun `Thor unbond throws when the entered amount exceeds the node bond`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0.50000001")

        shouldThrow<InvalidTransactionDataException> {
            build(Chain.ThorChain, state = thorUnbondState()).build()
        }
    }

    @Test
    fun `Thor unbond throws when the ceiling was measured on a different node`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "otherNode") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("otherNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0.5")

        shouldThrow<InvalidTransactionDataException> {
            build(Chain.ThorChain, state = thorUnbondState()).build()
        }
    }

    @Test
    fun `Thor unbond throws when no bonded position was loaded`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0.5")

        shouldThrow<InvalidTransactionDataException> { build(Chain.ThorChain).build() }
    }

    @Test
    fun `unbondRuneCeiling answers only the matching Thor node`() {
        val state = thorUnbondState(amount = BigInteger("1250000000"), node = "thor1a")

        state.unbondRuneCeiling("thor1a") shouldBe BigInteger("1250000000")
        state.unbondRuneCeiling("thor1b") shouldBe null
        state.copy(depositChain = Chain.MayaChain).unbondRuneCeiling("thor1a") shouldBe null
    }

    @Test
    fun `bondedRuneAmountForNode is zero when the node is absent`() {
        val nodes =
            listOf(
                BondedNodePosition(
                    id = "rune-thor1a",
                    node = BondedNodePosition.BondedNode(address = "thor1a", state = "Active"),
                    amount = BigInteger("5"),
                    coin = coin(Chain.ThorChain),
                    apy = 0.0,
                    nextReward = 0.0,
                    nextChurn = null,
                )
            )

        bondedRuneAmountForNode(nodes, "thor1a") shouldBe BigInteger("5")
        bondedRuneAmountForNode(nodes, "thor1b") shouldBe BigInteger.ZERO
    }

    private fun thorUnbondState(
        amount: BigInteger = BigInteger("50000000"),
        node: String = "thorNode",
    ) =
        DepositFormUiModel(
            depositChain = Chain.ThorChain,
            depositOption = DepositOption.Unbond,
            bondedRuneCeiling = BondedRuneCeiling(nodeAddress = node, amount = amount),
        )

    private fun build(
        chain: Chain,
        selectedTokenChain: Chain = Chain.ThorChain,
        state: DepositFormUiModel = DepositFormUiModel(depositChain = chain),
    ) =
        UnbondStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { chain },
            stateProvider = { state },
            selectedTokenProvider = { coin(selectedTokenChain) },
            nodeAddressFieldState = nodeAddress,
            tokenAmountFieldState = tokenAmount,
            providerFieldState = provider,
            assetsFieldState = assets,
            lpUnitsFieldState = lpUnits,
            chainAccountAddressRepository = chainRepo,
            blockChainSpecificRepository = specificRepo,
            isAssetCharsValid = assetsValidator,
            isLpUnitCharsValid = { it.toLongOrNull()?.let { v -> v > 0 } == true },
            calculateGasFee = { _, token, _ -> TokenValue(BigInteger.ONE, token) },
            getFeesFiatValue = { _, _, _ -> estimatedFee() },
        )

    private fun givenSpecific() {
        coEvery {
            specificRepo.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.ZERO,
                    isDeposit = true,
                    transactionType =
                        vultisig.keysign.v1.TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                )
            )
    }

    private fun estimatedFee() =
        EstimatedGasFee(
            formattedFiatValue = "$0.01",
            formattedTokenValue = "0.0001",
            tokenValue = TokenValue(BigInteger.ONE, coin(Chain.ThorChain)),
            fiatValue = mockk(relaxed = true),
        )

    private fun coin(chain: Chain): Coin =
        Coin(
            chain = chain,
            ticker = if (chain == Chain.MayaChain) "CACAO" else "RUNE",
            logo = "",
            address = "self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )
}
