@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_BOND
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class BondStrategyTest {

    private val nodeAddress = TextFieldState()
    private val tokenAmount = TextFieldState()
    private val provider = TextFieldState()
    private val assets = TextFieldState()
    private val lpUnits = TextFieldState()
    private val operatorFee = TextFieldState()

    private val chainRepo: ChainAccountAddressRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val assetsValidator: DepositMemoAssetsValidatorUseCase = mockk()

    @Test
    fun `Thor bond memo scales operator fee by 100 and uses tokenAmount as srcTokenValue`() =
        runTest {
            coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
            givenSpecific()
            nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
            tokenAmount.setTextAndPlaceCursorAtEnd("1")
            operatorFee.setTextAndPlaceCursorAtEnd("5")

            val tx = build(Chain.ThorChain).build()

            assertEquals("BOND:thorNode::500", tx.memo)
            assertEquals(BigInteger.valueOf(100_000_000), tx.srcTokenValue.value)
            assertEquals(OPERATION_BOND, tx.operation)
            assertEquals("thorNode", tx.nodeAddress)
        }

    @Test
    fun `Maya bond memo uses assets and lpUnits and srcTokenValue defaults to 1`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        every { assetsValidator.invoke("MAYA.CACAO") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")
        assets.setTextAndPlaceCursorAtEnd("MAYA.CACAO")
        lpUnits.setTextAndPlaceCursorAtEnd("1000")

        val tx = build(Chain.MayaChain).build()

        assertEquals("BOND:MAYA.CACAO:1000:mayaNode", tx.memo)
        assertEquals(BigInteger.ONE, tx.srcTokenValue.value)
    }

    @Test
    fun `Thor bond throws when token amount is missing or zero`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0")

        assertFailsWith<InvalidTransactionDataException> { build(Chain.ThorChain).build() }
    }

    @Test
    fun `bond throws when whitelist check failed`() = runTest {
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")

        assertFailsWith<InvalidTransactionDataException> {
            build(Chain.ThorChain, isWhitelistFailed = true).build()
        }
    }

    private fun build(chain: Chain, isWhitelistFailed: Boolean = false) =
        BondStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { chain },
            stateProvider = {
                DepositFormUiModel(depositChain = chain, isWhitelistFailed = isWhitelistFailed)
            },
            selectedTokenProvider = { runeCoin() },
            nodeAddressFieldState = nodeAddress,
            tokenAmountFieldState = tokenAmount,
            providerFieldState = provider,
            assetsFieldState = assets,
            lpUnitsFieldState = lpUnits,
            operatorFeeFieldState = operatorFee,
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
            tokenValue = TokenValue(BigInteger.ONE, runeCoin()),
            fiatValue = mockk(relaxed = true),
        )

    private fun runeCoin(): Coin =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "",
            address = "thor1self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        )
}
