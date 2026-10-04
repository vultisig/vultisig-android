package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CheckMayaLpPairingUseCase
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightBlock
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.MayaLpPairing
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
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal class AddMayaLiquidityStrategyTest {

    private val tokenAmount = TextFieldState()
    private val accountsRepository: AccountsRepository = mockk()
    private val vaultRepository: VaultRepository = mockk()
    private val mayaChainApi: MayaChainApi = mockk()
    private val preflight: MayaChainLpPreflightUseCase = mockk()
    private val checkPairing: CheckMayaLpPairingUseCase = mockk()
    private val feeServiceComposite: FeeServiceComposite = mockk()
    private val tokenRepository: TokenRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()

    @BeforeEach
    fun setUp() {
        tokenAmount.setTextAndPlaceCursorAtEnd("0.01")
        givenVault(withCacao = true)
        coEvery { preflight(any(), any()) } returns null
        coEvery { checkPairing(any(), any(), any()) } returns MayaLpPairing.Pairable
        coEvery { mayaChainApi.getInboundAddresses() } returns
            listOf(inbound("BTC", BTC_INBOUND), inbound("ETH", ETH_INBOUND, dust = "0"))
        coEvery { feeServiceComposite.calculateFees(any()) } returns BasicFee(BigInteger.TEN)
        coEvery { tokenRepository.getNativeToken(any()) } returns
            nativeCoin(Chain.Bitcoin, decimals = 8, address = "")
        coEvery {
            specificRepo.getSpecific(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns BlockChainSpecificAndUtxo(mockk(relaxed = true))
    }

    @Test
    fun `asset side sends the native coin to the live Maya inbound naming the CACAO address`() =
        runTest {
            givenNativeAccount(Chain.Ethereum, decimals = 18, address = ETH_ADDRESS)

            val tx = build(Chain.Ethereum).build()

            assertEquals("+:ETH.ETH:$CACAO_ADDRESS", tx.memo)
            assertEquals(ETH_INBOUND, tx.dstAddress)
            assertEquals("ETH.ETH", tx.pool)
            assertEquals(CACAO_ADDRESS, tx.pairedAddress)
            assertEquals(OPERATION_MINT, tx.operation)
            assertEquals(ETH_ADDRESS, tx.srcAddress)
            assertEquals(BigInteger("10000000000000000"), tx.srcTokenValue.value)
            coVerify(exactly = 1) { preflight("ETH.ETH", true) }
        }

    @Test
    fun `Arbitrum ETH joins the ARB pool through the ARB inbound`() = runTest {
        givenNativeAccount(Chain.Arbitrum, decimals = 18, address = ETH_ADDRESS)
        coEvery { mayaChainApi.getInboundAddresses() } returns
            listOf(inbound("ETH", ETH_INBOUND), inbound("ARB", ARB_INBOUND, dust = "0"))

        val tx = build(Chain.Arbitrum).build()

        assertEquals("+:ARB.ETH:$CACAO_ADDRESS", tx.memo)
        assertEquals(ARB_INBOUND, tx.dstAddress)
    }

    @Test
    fun `Bitcoin above the inbound dust threshold is planned and built`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)

        val tx = build(Chain.Bitcoin).build()

        assertEquals("+:BTC.BTC:$CACAO_ADDRESS", tx.memo)
        assertEquals(BTC_INBOUND, tx.dstAddress)
    }

    @Test
    fun `a vault without a MayaChain account cannot pair the deposit`() = runTest {
        givenVault(withCacao = false)
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)

        assertStringError(R.string.deposit_error_mayachain_not_enabled_for_lp) {
            build(Chain.Bitcoin).build()
        }
    }

    @Test
    fun `a halted inbound refuses the deposit`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)
        coEvery { mayaChainApi.getInboundAddresses() } returns
            listOf(inbound("BTC", BTC_INBOUND, halted = true))

        assertFormattedError(R.string.deposit_error_maya_chain_halted) {
            build(Chain.Bitcoin).build()
        }
    }

    @Test
    fun `an unreachable mayanode fails closed`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)
        coEvery { mayaChainApi.getInboundAddresses() } throws RuntimeException("timeout")

        assertStringError(R.string.deposit_error_maya_inbound_unavailable) {
            build(Chain.Bitcoin).build()
        }
    }

    @Test
    fun `a chain missing from the inbound set is not available`() = runTest {
        givenNativeAccount(Chain.Zcash, decimals = 8, address = "t1self")

        assertFormattedError(R.string.deposit_error_pool_not_available) {
            build(Chain.Zcash).build()
        }
    }

    @Test
    fun `an 8-decimal deposit below the inbound dust threshold is refused`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)
        tokenAmount.setTextAndPlaceCursorAtEnd("0.00009")

        assertFormattedError(R.string.send_form_minimum_send_amount_is_requires_this) {
            build(Chain.Bitcoin).build()
        }
    }

    @Test
    fun `an 18-decimal deposit is compared to the dust threshold in 1e8 fixed point`() = runTest {
        givenNativeAccount(Chain.Ethereum, decimals = 18, address = ETH_ADDRESS)
        coEvery { mayaChainApi.getInboundAddresses() } returns
            listOf(inbound("ETH", ETH_INBOUND, dust = "10000"))

        tokenAmount.setTextAndPlaceCursorAtEnd("0.00009")
        assertFormattedError(R.string.send_form_minimum_send_amount_is_requires_this) {
            build(Chain.Ethereum).build()
        }

        tokenAmount.setTextAndPlaceCursorAtEnd("0.0001")
        assertEquals(ETH_INBOUND, build(Chain.Ethereum).build().dstAddress)
    }

    @Test
    fun `a preflight block stops the build before the inbound is fetched`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)
        coEvery { preflight("BTC.BTC", true) } returns
            MayaChainLpPreflightBlock.LpPaused("BTC.BTC")

        assertFormattedError(R.string.deposit_error_maya_lp_paused_pool) {
            build(Chain.Bitcoin).build()
        }
        coVerify(exactly = 0) { mayaChainApi.getInboundAddresses() }
    }

    @Test
    fun `an asset deposit into a live CACAO-only position is refused before signing`() =
        runTest {
            givenNativeAccount(Chain.Zcash, decimals = 8, address = ZEC_ADDRESS)
            coEvery { checkPairing("ZEC.ZEC", CACAO_ADDRESS, ZEC_ADDRESS) } returns
                MayaLpPairing.SingleSidedPosition

            assertFormattedError(R.string.deposit_error_maya_lp_single_sided_position) {
                build(Chain.Zcash).build()
            }
            coVerify(exactly = 0) { mayaChainApi.getInboundAddresses() }
        }

    @Test
    fun `an asset deposit into a record paired elsewhere is refused`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)
        coEvery { checkPairing("BTC.BTC", CACAO_ADDRESS, BTC_ADDRESS) } returns
            MayaLpPairing.AddressMismatch

        assertFormattedError(R.string.deposit_error_maya_lp_address_mismatch) {
            build(Chain.Bitcoin).build()
        }
    }

    @Test
    fun `an asset deposit refuses when the record cannot be read`() = runTest {
        givenNativeAccount(Chain.Bitcoin, decimals = 8, address = BTC_ADDRESS)
        coEvery { checkPairing(any(), any(), any()) } throws RuntimeException("timeout")

        assertStringError(R.string.deposit_error_maya_lp_unverified) {
            build(Chain.Bitcoin).build()
        }
    }

    @Test
    fun `a UTXO deposit reports the planned fee, not the per-byte rate it signs with`() = runTest {
        // Zcash's fee service seeds a flat rate; the planner adds the ZIP-317 charge for the memo
        // output (2026-10-04 mainnet: shown 1,000 zats, paid 20,020).
        givenNativeAccount(Chain.Zcash, decimals = 8, address = ZEC_ADDRESS)
        coEvery { mayaChainApi.getInboundAddresses() } returns listOf(inbound("ZEC", ZEC_INBOUND))

        val tx = build(Chain.Zcash, planFee = 20_020L).build()

        assertEquals(BigInteger.valueOf(20_020L), tx.estimatedFees.value)
        coVerify {
            specificRepo.getSpecific(
                Chain.Zcash,
                any(),
                any(),
                match { it.value == BigInteger.TEN },
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `a chain the planner does not cover keeps the fee service's figure`() = runTest {
        givenNativeAccount(Chain.Ethereum, decimals = 18, address = ETH_ADDRESS)

        val tx = build(Chain.Ethereum).build()

        assertEquals(BigInteger.TEN, tx.estimatedFees.value)
    }

    private fun build(chain: Chain, planFee: Long = 0L) =
        AddMayaLiquidityStrategy(
            vaultIdProvider = { VAULT_ID },
            chainProvider = { chain },
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepository,
            vaultRepository = vaultRepository,
            mayaChainApi = mayaChainApi,
            mayaChainLpPreflight = preflight,
            checkMayaLpPairing = checkPairing,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            blockChainSpecificRepository = specificRepo,
            gasFeeToEstimate = { params ->
                EstimatedGasFee(
                    formattedFiatValue = "$0.01",
                    formattedTokenValue = "0.0001",
                    tokenValue = params.gasFee,
                    fiatValue = mockk(relaxed = true),
                )
            },
            getBitcoinTransactionPlan = { _, _, _, _, _, _ ->
                Bitcoin.TransactionPlan.newBuilder()
                    .setFee(planFee)
                    .setError(SigningError.OK)
                    .build()
            },
        )

    private fun givenVault(withCacao: Boolean) {
        val coins =
            if (withCacao) listOf(nativeCoin(Chain.MayaChain, 10, CACAO_ADDRESS)) else emptyList()
        coEvery { vaultRepository.get(VAULT_ID) } returns
            Vault(id = VAULT_ID, name = "vault", coins = coins)
    }

    private fun givenNativeAccount(chain: Chain, decimals: Int, address: String) {
        val coin = nativeCoin(chain, decimals, address)
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

    private fun nativeCoin(chain: Chain, decimals: Int, address: String): Coin =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = address,
            decimal = decimals,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun inbound(
        chain: String,
        address: String,
        halted: Boolean = false,
        dust: String = "10000",
    ) =
        THORChainInboundAddress(
            chain = chain,
            address = address,
            halted = halted,
            dustThreshold = dust,
        )

    private suspend fun assertStringError(resId: Int, block: suspend () -> Unit) {
        val text = assertFailsWith<InvalidTransactionDataException> { block() }.text
        assertTrue(text is UiText.StringResource)
        assertEquals(resId, text.resId)
    }

    private suspend fun assertFormattedError(resId: Int, block: suspend () -> Unit) {
        val text = assertFailsWith<InvalidTransactionDataException> { block() }.text
        assertTrue(text is UiText.FormattedText)
        assertEquals(resId, text.resId)
    }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val CACAO_ADDRESS = "maya1self"
        const val BTC_ADDRESS = "bc1qself"
        const val ETH_ADDRESS = "0xself"
        const val ZEC_ADDRESS = "t1self"
        const val BTC_INBOUND = "bc1qinbound"
        const val ETH_INBOUND = "0xinbound"
        const val ARB_INBOUND = "0xarbinbound"
        const val ZEC_INBOUND = "t1inbound"
    }
}
