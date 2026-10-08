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
import com.vultisig.wallet.data.usecases.CheckMayaLpPairingUseCase
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightBlock
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.MayaLpPairing
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
    private val mayaPreflight: MayaChainLpPreflightUseCase = mockk()
    private val checkPairing: CheckMayaLpPairingUseCase = mockk()
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
        coEvery { mayaPreflight(any(), any()) } returns null
        coEvery { checkPairing(any(), any(), any()) } returns MayaLpPairing.Pairable
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
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)

        build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL).build()

        coVerify(exactly = 0) { preflight(any(), any()) }
    }

    @Test
    fun `CACAO add to a native Maya pool names the asset address and asks mayanode`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)

        val tx = build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL).build()

        assertEquals("+:$MAYA_BTC_POOL:$BTC_ADDRESS", tx.memo)
        assertEquals(BTC_ADDRESS, tx.pairedAddress)
        coVerify(exactly = 1) { mayaPreflight(MAYA_BTC_POOL, true) }
    }

    @Test
    fun `CACAO add to a native Maya pool without a resolved asset address is refused`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(Chain.MayaChain, pairedAddress = null, pool = MAYA_BTC_POOL).build()
            }

        val text = error.text
        assertTrue(text is UiText.StringResource)
        assertEquals(R.string.send_error_no_address, text.resId)
    }

    @Test
    fun `CACAO add to a Maya pool the app cannot pair stays single-sided`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)

        val tx = build(Chain.MayaChain, pairedAddress = null, pool = MAYA_USDC_POOL).build()

        assertEquals("+:$MAYA_USDC_POOL", tx.memo)
        coVerify(exactly = 1) { mayaPreflight(MAYA_USDC_POOL, false) }
    }

    @Test
    fun `a mayanode preflight block surfaces the MayaChain reason`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)
        coEvery { mayaPreflight(MAYA_BTC_POOL, true) } returns
            MayaChainLpPreflightBlock.LpPaused(MAYA_BTC_POOL)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL).build()
            }

        val text = error.text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.deposit_error_maya_lp_paused_pool, text.resId)
    }

    @Test
    fun `CACAO add into a live CACAO-only position stays single-sided`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)
        coEvery { checkPairing(MAYA_BTC_POOL, CACAO_ADDRESS, BTC_ADDRESS) } returns
            MayaLpPairing.SingleSidedPosition

        val tx = build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL).build()

        assertEquals("+:$MAYA_BTC_POOL", tx.memo)
        assertEquals("", tx.pairedAddress)
        coVerify(exactly = 1) { mayaPreflight(MAYA_BTC_POOL, false) }
    }

    @Test
    fun `CACAO add into a record paired elsewhere is refused`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)
        coEvery { checkPairing(MAYA_BTC_POOL, CACAO_ADDRESS, BTC_ADDRESS) } returns
            MayaLpPairing.AddressMismatch

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL)
                        .build()
                }
                .text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.deposit_error_maya_lp_address_mismatch, text.resId)
    }

    @Test
    fun `CACAO add refuses when the record cannot be read`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)
        coEvery { checkPairing(any(), any(), any()) } throws RuntimeException("timeout")

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL)
                        .build()
                }
                .text
        assertTrue(text is UiText.StringResource)
        assertEquals(R.string.deposit_error_maya_lp_unverified, text.resId)
    }

    @Test
    fun `THORChain adds never read the Maya record`() = runTest {
        givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

        build(Chain.ThorChain, pairedAddress = ETH_ADDRESS).build()

        coVerify(exactly = 0) { checkPairing(any(), any(), any()) }
    }

    private fun build(chain: Chain, pairedAddress: String?, pool: String = STAGED_POOL) =
        AddLiquidityStrategy(
            vaultIdProvider = { VAULT_ID },
            chainProvider = { chain },
            lpPoolIdProvider = { pool },
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepository,
            thorChainLpPreflight = preflight,
            mayaChainLpPreflight = mayaPreflight,
            checkMayaLpPairing = checkPairing,
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
        const val CACAO_ADDRESS = "maya1self"
        const val BTC_ADDRESS = "bc1qself"
        const val MAYA_BTC_POOL = "BTC.BTC"
        const val MAYA_USDC_POOL = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"
    }
}
