@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class StakeStrategyTest {

    private val nodeAddress = TextFieldState()
    private val tokenAmount = TextFieldState()

    private val accountsRepo: AccountsRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val tonStakingApi: TonStakingApi = mockk()

    @Test
    fun `whales deposit uses Deposit comment, bounceable dest and scaled amount`() = runTest {
        givenPool(implementation = "whales", minStake = 0)
        givenAccounts()
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        val tx = stake().build()

        assertEquals("Deposit", tx.memo)
        assertEquals(tonCoin(), tx.srcToken)
        assertEquals("EQ_0:pool", tx.dstAddress)
        assertEquals(BigInteger.valueOf(1_000_000_000), tx.srcTokenValue.value)
    }

    @Test
    fun `tf deposit uses d comment`() = runTest {
        givenPool(implementation = "tf", minStake = 0)
        givenAccounts()
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        assertEquals("d", stake().build().memo)
    }

    @Test
    fun `whales withdraw uses Withdraw comment and fixed 0_2 TON fee`() = runTest {
        givenPool(implementation = "whales", minStake = 50_000_000_000)
        givenAccounts()
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        // Entered amount is ignored for withdraw.
        tokenAmount.setTextAndPlaceCursorAtEnd("999")

        val tx = unstake().build()

        assertEquals("Withdraw", tx.memo)
        assertEquals(BigInteger.valueOf(200_000_000), tx.srcTokenValue.value)
    }

    @Test
    fun `deposit blocked for unsupported pool implementation`() = runTest {
        givenPool(implementation = "liquidTF", minStake = 0)
        givenAccounts()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("100")

        val ex = assertFailsWith<InvalidTransactionDataException> { stake().build() }
        assertEquals(
            R.string.ton_stake_error_unsupported_pool,
            (ex.text as UiText.StringResource).resId,
        )
    }

    @Test
    fun `deposit blocked when pool is unknown to tonapi`() = runTest {
        coEvery { tonStakingApi.getStakingPool(any()) } returns null
        givenAccounts()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("100")

        val ex = assertFailsWith<InvalidTransactionDataException> { stake().build() }
        assertEquals(
            R.string.ton_stake_error_unsupported_pool,
            (ex.text as UiText.StringResource).resId,
        )
    }

    @Test
    fun `deposit below pool minimum is rejected`() = runTest {
        givenPool(implementation = "whales", minStake = 50_000_000_000)
        givenAccounts()
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        // 50 TON < min_stake (50) + 1 TON commission.
        tokenAmount.setTextAndPlaceCursorAtEnd("50")

        val ex = assertFailsWith<InvalidTransactionDataException> { stake().build() }
        assertEquals(R.string.ton_stake_error_min_amount, (ex.text as UiText.FormattedText).resId)
    }

    @Test
    fun `stake throws when deposit chain is not Ton`() = runTest {
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("100")

        assertFailsWith<InvalidTransactionDataException> {
            stake(depositChain = Chain.ThorChain).build()
        }
    }

    @Test
    fun `stake throws when node address is blank`() = runTest {
        tokenAmount.setTextAndPlaceCursorAtEnd("100")

        assertFailsWith<InvalidTransactionDataException> { stake().build() }
    }

    @Test
    fun `stake throws when token amount is missing or zero`() = runTest {
        givenPool(implementation = "whales", minStake = 0)
        givenAccounts()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("0")

        assertFailsWith<InvalidTransactionDataException> { stake().build() }
    }

    @Test
    fun `stake throws when no native token account exists`() = runTest {
        givenPool(implementation = "whales", minStake = 0)
        coEvery { accountsRepo.loadAddress("vault-1", Chain.Ton) } returns
            flowOf(Address(chain = Chain.Ton, address = "0:pool", accounts = emptyList()))
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        assertFailsWith<InvalidTransactionDataException> { stake().build() }
    }

    private fun stake(depositChain: Chain = Chain.Ton) =
        StakeStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { Chain.Ton },
            stateProvider = { DepositFormUiModel(depositChain = depositChain) },
            nodeAddressFieldState = nodeAddress,
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepo,
            tonStakingApi = tonStakingApi,
            toBounceableAddress = { "EQ_$it" },
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, token, _ -> TokenValue(BigInteger.ONE, token) },
            getFeesFiatValue = { _, _, _ -> estimatedFee() },
        )

    private fun unstake(depositChain: Chain = Chain.Ton) =
        UnstakeStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { Chain.Ton },
            stateProvider = { DepositFormUiModel(depositChain = depositChain) },
            nodeAddressFieldState = nodeAddress,
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepo,
            tonStakingApi = tonStakingApi,
            toBounceableAddress = { "EQ_$it" },
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, token, _ -> TokenValue(BigInteger.ONE, token) },
            getFeesFiatValue = { _, _, _ -> estimatedFee() },
        )

    private fun givenPool(implementation: String, minStake: Long) {
        coEvery { tonStakingApi.getStakingPool(any()) } returns
            TonStakingPoolInfoJson(implementation = implementation, minStake = minStake)
    }

    private fun givenAccounts() {
        coEvery { accountsRepo.loadAddress("vault-1", Chain.Ton) } returns
            flowOf(
                Address(
                    chain = Chain.Ton,
                    address = "tonSelf",
                    accounts =
                        listOf(
                            Account(
                                token = tonCoin(),
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
    }

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
                // Pin the bounceable EQ destination so the test fails if the strategy stops
                // forwarding it into the spec.
                dstAddress = "EQ_0:pool",
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Ton(
                    sequenceNumber = 0UL,
                    expireAt = 0UL,
                    bounceable = true,
                    sendMaxAmount = false,
                )
            )
    }

    private fun estimatedFee() =
        EstimatedGasFee(
            formattedFiatValue = "$0.01",
            formattedTokenValue = "0.0001",
            tokenValue = TokenValue(BigInteger.ONE, tonCoin()),
            fiatValue = mockk(relaxed = true),
        )

    private fun tonCoin(): Coin =
        Coin(
            chain = Chain.Ton,
            ticker = "TON",
            logo = "",
            address = "tonSelf",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "the-open-network",
            contractAddress = "",
            isNativeToken = true,
        )
}
