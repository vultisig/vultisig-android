package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightBlock
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AddLiquidityStrategyTest {

    private val tokenAmount = TextFieldState()
    private val accountsRepository: AccountsRepository = mockk()
    private val preflight: ThorChainLpPreflightUseCase = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()

    @BeforeEach
    fun setUp() {
        tokenAmount.setTextAndPlaceCursorAtEnd("1")
        coEvery {
            specificRepo.getSpecific(any(), any(), any(), any(), any(), any(), any())
        } returns BlockChainSpecificAndUtxo(mockk(relaxed = true))
        // Mirrors thornode for a Staged pool: only an add naming the other side gets through.
        coEvery { preflight(STAGED_POOL, true) } returns null
        coEvery { preflight(STAGED_POOL, false) } returns
            ThorChainLpPreflightBlock.StagedPoolRequiresPairedAdd(STAGED_POOL)
    }

    @Test
    fun `RUNE-side add to a staged pool goes through when the asset address is paired`() =
        runTest {
            givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

            val tx = build(Chain.ThorChain, pairedAddress = ETH_ADDRESS).build()

            assertEquals("+:$STAGED_POOL:$ETH_ADDRESS", tx.memo)
            assertEquals(ETH_ADDRESS, tx.pairedAddress)
            coVerify(exactly = 1) { preflight(STAGED_POOL, true) }
        }

    @Test
    fun `asset-side add to a staged pool goes through when the RUNE address is paired`() =
        runTest {
            givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)

            val tx = build(Chain.Ethereum, pairedAddress = RUNE_ADDRESS).build()

            assertEquals("+:$STAGED_POOL:$RUNE_ADDRESS", tx.memo)
            assertEquals(RUNE_ADDRESS, tx.pairedAddress)
            coVerify(exactly = 1) { preflight(STAGED_POOL, true) }
        }

    @Test
    fun `unpaired add to a staged pool is blocked with the staged-pool reason`() = runTest {
        givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(Chain.ThorChain, pairedAddress = null).build()
            }

        val text = error.text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.deposit_error_pool_staged_unpaired, text.resId)
    }

    @Test
    fun `Maya add never consults the THORChain preflight`() = runTest {
        givenNativeAccount(Chain.MayaChain, "maya1self")

        build(Chain.MayaChain, pairedAddress = null, pool = "BTC.BTC").build()

        coVerify(exactly = 0) { preflight(any(), any()) }
    }

    private fun build(chain: Chain, pairedAddress: String?, pool: String = STAGED_POOL) =
        AddLiquidityStrategy(
            vaultIdProvider = { VAULT_ID },
            chainProvider = { chain },
            lpPoolIdProvider = { pool },
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepository,
            thorChainLpPreflight = preflight,
            resolvePairedAddress = { _, _, _ -> pairedAddress },
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, coin, _ -> TokenValue(BigInteger.ONE, coin) },
            getFeesFiatValue = { _, gasFee, _ -> estimatedFee(gasFee) },
        )

    private fun givenNativeAccount(chain: Chain, address: String) {
        val coin = nativeCoin(chain, address)
        coEvery { accountsRepository.loadAddress(VAULT_ID, chain) } returns
            flowOf(
                Address(
                    chain = chain,
                    address = address,
                    accounts =
                        listOf(
                            Account(token = coin, tokenValue = null, fiatValue = null, price = null)
                        ),
                )
            )
    }

    private fun nativeCoin(chain: Chain, address: String): Coin =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = address,
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun estimatedFee(gasFee: TokenValue) =
        EstimatedGasFee(
            formattedFiatValue = "$0.01",
            formattedTokenValue = "0.0001",
            tokenValue = gasFee,
            fiatValue = mockk(relaxed = true),
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val STAGED_POOL = "ETH.LINK-0X514910771AF9CA656AF840DFF83E8264ECF986CA"
        const val RUNE_ADDRESS = "thor1self"
        const val ETH_ADDRESS = "0xself"
    }
}
