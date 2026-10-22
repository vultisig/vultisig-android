package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.google.protobuf.ByteString
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CheckMayaLpPairingUseCase
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightBlock
import com.vultisig.wallet.data.usecases.MayaChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.MayaLpPairing
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightBlock
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
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

internal class AddLiquidityStrategyTest {

    private val fields = DepositFieldStates()
    private val tokenAmount = fields.tokenAmountFieldState
    private val accountsRepository: AccountsRepository = mockk()
    private val preflight: ThorChainLpPreflightUseCase = mockk()
    private val mayaPreflight: MayaChainLpPreflightUseCase = mockk()
    private val checkPairing: CheckMayaLpPairingUseCase = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val thorChainApi: ThorChainApi = mockk()
    private val vaultRepository: VaultRepository = mockk()
    private val feeServiceComposite: FeeServiceComposite = mockk()
    private val tokenRepository: TokenRepository = mockk()

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
        coEvery { preflight(ETH_POOL, any()) } returns null
        coEvery { preflight(BTC_POOL, any()) } returns null
        coEvery { mayaPreflight(any(), any()) } returns null
        coEvery { checkPairing(any(), any(), any()) } returns MayaLpPairing.Pairable
        coEvery { vaultRepository.get(VAULT_ID) } returns Vault(id = VAULT_ID, name = "vault")
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("BTC", BTC_INBOUND), inbound("ETH", ETH_INBOUND))
        coEvery { feeServiceComposite.calculateFees(any()) } returns BasicFee(BigInteger.TEN)
        coEvery { tokenRepository.getNativeToken(any()) } returns
            nativeCoin(Chain.Bitcoin, address = "")
        givenInboundSpecific(mockk(relaxed = true))
    }

    @Test
    fun `RUNE-side add to a staged pool goes through when the asset address is paired`() = runTest {
        givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

        val tx = build(Chain.ThorChain, pairedAddress = ETH_ADDRESS).build()

        assertEquals("+:$STAGED_POOL:$ETH_ADDRESS", tx.memo)
        assertEquals(ETH_ADDRESS, tx.pairedAddress)
        coVerify(exactly = 1) { preflight(STAGED_POOL, true) }
    }

    @Test
    fun `asset-side EVM add goes to the live inbound naming the RUNE address`() = runTest {
        givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)

        val tx = build(Chain.Ethereum, pairedAddress = RUNE_ADDRESS, pool = ETH_POOL).build()

        assertEquals(ETH_INBOUND, tx.dstAddress)
        assertEquals("+:$ETH_POOL:$RUNE_ADDRESS", tx.memo)
        assertEquals(RUNE_ADDRESS, tx.pairedAddress)
        assertEquals(ETH_POOL, tx.pool)
        assertEquals(BigInteger.TEN, tx.estimatedFees.value)
        coVerify(exactly = 1) { preflight(ETH_POOL, true) }
        coVerify {
            specificRepo.getSpecific(
                chain = Chain.Ethereum,
                address = ETH_ADDRESS,
                token = any(),
                gasFee = any(),
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                gasLimit = any(),
                dstAddress = ETH_INBOUND,
                tokenAmountValue = BigInteger("100000000"),
                memo = "+:$ETH_POOL:$RUNE_ADDRESS",
                transactionType = any(),
                isThorchainRouterDeposit = false,
            )
        }
    }

    @Test
    fun `asset-side UTXO add spends the planned inputs`() = runTest {
        givenNativeAccount(Chain.Bitcoin, BTC_ADDRESS)
        givenInboundSpecific(
            BlockChainSpecific.UTXO(byteFee = BigInteger.TEN, sendMaxAmount = false),
            utxos = listOf(UtxoInfo(hash = "00", index = 0u, amount = 1L)),
        )

        val tx = build(Chain.Bitcoin, pairedAddress = RUNE_ADDRESS, pool = BTC_POOL).build()

        assertEquals(BTC_INBOUND, tx.dstAddress)
        assertEquals("+:$BTC_POOL:$RUNE_ADDRESS", tx.memo)
        assertEquals(
            listOf(UtxoInfo(hash = PLANNED_UTXO_HASH, index = 1u, amount = 200_000L)),
            tx.utxos,
        )
        assertEquals(BigInteger.valueOf(PLAN_FEE), tx.estimatedFees.value)
    }

    @Test
    fun `asset-side add refuses a halted chain`() = runTest {
        assertAssetSideRefusedBy(inbound("ETH", ETH_INBOUND, halted = true))
    }

    @Test
    fun `asset-side add refuses a chain with LP actions paused`() = runTest {
        assertAssetSideRefusedBy(inbound("ETH", ETH_INBOUND, lpPaused = true))
    }

    @Test
    fun `asset-side add refuses a chain with trading paused`() = runTest {
        assertAssetSideRefusedBy(inbound("ETH", ETH_INBOUND, tradingPaused = true))
    }

    @Test
    fun `asset-side add fails closed when thornode is unreachable`() = runTest {
        givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } throws RuntimeException("timeout")

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.Ethereum, pairedAddress = RUNE_ADDRESS, pool = ETH_POOL).build()
                }
                .text
        assertTrue(text is UiText.StringResource)
        assertEquals(R.string.deposit_error_thorchain_inbound_unavailable, text.resId)
    }

    @Test
    fun `asset-side add on a chain missing from the inbound set is not available`() = runTest {
        givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("BTC", BTC_INBOUND))

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.Ethereum, pairedAddress = RUNE_ADDRESS, pool = ETH_POOL).build()
                }
                .text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.deposit_error_pool_not_available, text.resId)
    }

    @Test
    fun `asset-side add at the inbound dust threshold is refused`() = runTest {
        givenNativeAccount(Chain.Bitcoin, BTC_ADDRESS)
        tokenAmount.setTextAndPlaceCursorAtEnd("0.0001")

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.Bitcoin, pairedAddress = RUNE_ADDRESS, pool = BTC_POOL).build()
                }
                .text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.send_form_minimum_send_amount_is_requires_this, text.resId)
        assertEquals("0.00010001", text.formatArgs.first())
    }

    @Test
    fun `asset-side add to a token pool is refused before anything is read`() = runTest {
        givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.Ethereum, pairedAddress = RUNE_ADDRESS).build()
                }
                .text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.deposit_error_lp_token_pool_asset_side, text.resId)
        coVerify(exactly = 0) { preflight(any(), any()) }
        coVerify(exactly = 0) { thorChainApi.getTHORChainInboundAddresses() }
    }

    @Test
    fun `RUNE-side add stays a MsgDeposit with no destination`() = runTest {
        givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

        val tx = build(Chain.ThorChain, pairedAddress = BTC_ADDRESS, pool = BTC_POOL).build()

        assertEquals("", tx.dstAddress)
        assertEquals("+:$BTC_POOL:$BTC_ADDRESS", tx.memo)
        coVerify(exactly = 0) { thorChainApi.getTHORChainInboundAddresses() }
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
    fun `RUNE-side add that leaves no room for the fee is refused`() = runTest {
        givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(Chain.ThorChain, pairedAddress = ETH_ADDRESS, formBalance = ONE_COIN).build()
            }

        assertInsufficientBalance(error)
    }

    @Test
    fun `asset-side add that leaves no room for the fee is refused`() = runTest {
        givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(
                        Chain.Ethereum,
                        pairedAddress = RUNE_ADDRESS,
                        pool = ETH_POOL,
                        formBalance = ONE_COIN,
                    )
                    .build()
            }

        assertInsufficientBalance(error)
    }

    @Test
    fun `add the balance covers with the fee keeps the amount and empty destination`() = runTest {
        givenNativeAccount(Chain.ThorChain, RUNE_ADDRESS)

        val tx =
            build(
                    Chain.ThorChain,
                    pairedAddress = ETH_ADDRESS,
                    formBalance = ONE_COIN + BigInteger.ONE,
                )
                .build()

        assertEquals(ONE_COIN, tx.srcTokenValue.value)
        assertEquals("", tx.dstAddress)
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
    fun `CACAO add to a native Maya pool without the asset chain enabled names that chain`() =
        runTest {
            givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)

            val error =
                assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.MayaChain, pairedAddress = null, pool = MAYA_BTC_POOL).build()
                }

            val text = error.text
            assertTrue(text is UiText.FormattedText)
            assertEquals(R.string.deposit_error_lp_paired_chain_not_enabled, text.resId)
            assertEquals(listOf(Chain.Bitcoin.raw, MAYA_BTC_POOL), text.formatArgs)
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
    fun `CACAO add into a live position paired elsewhere falls back to single-sided`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)
        coEvery { checkPairing(MAYA_BTC_POOL, CACAO_ADDRESS, BTC_ADDRESS) } returns
            MayaLpPairing.PairedWithOtherAddress

        val tx = build(Chain.MayaChain, pairedAddress = BTC_ADDRESS, pool = MAYA_BTC_POOL).build()

        assertEquals("+:$MAYA_BTC_POOL", tx.memo)
        assertEquals("", tx.pairedAddress)
    }

    @Test
    fun `CACAO top-up of a live position needs no asset chain enabled`() = runTest {
        givenNativeAccount(Chain.MayaChain, CACAO_ADDRESS)
        coEvery { checkPairing(MAYA_BTC_POOL, CACAO_ADDRESS, null) } returns
            MayaLpPairing.SingleSidedPosition

        val tx = build(Chain.MayaChain, pairedAddress = null, pool = MAYA_BTC_POOL).build()

        assertEquals("+:$MAYA_BTC_POOL", tx.memo)
        assertEquals("", tx.pairedAddress)
        coVerify(exactly = 1) { mayaPreflight(MAYA_BTC_POOL, false) }
    }

    @Test
    fun `CACAO add against a pending half fixed to other addresses is refused`() = runTest {
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

    private fun build(
        chain: Chain,
        pairedAddress: String?,
        pool: String = STAGED_POOL,
        formBalance: BigInteger = ONE_COIN * BigInteger.TEN,
    ) =
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
            addressProvider = {
                gateAddress(gateAccount(nativeCoin(chain, addressOf(chain)), formBalance))
            },
            requireTokenAmount = amountGate(chain, fields)::requireTokenAmount,
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, coin, _ -> TokenValue(BigInteger.ONE, coin) },
            getFeesFiatValue = { _, gasFee, _ -> estimatedFee(gasFee) },
            thorChainApi = thorChainApi,
            vaultRepository = vaultRepository,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            gasFeeToEstimate = { params -> estimatedFee(params.gasFee) },
            getBitcoinTransactionPlan = { _, _, _, _, _, _ ->
                Bitcoin.TransactionPlan.newBuilder()
                    .setFee(PLAN_FEE)
                    .setError(SigningError.OK)
                    .addUtxos(
                        Bitcoin.UnspentTransaction.newBuilder()
                            .setOutPoint(
                                Bitcoin.OutPoint.newBuilder()
                                    .setHash(ByteString.copyFrom(byteArrayOf(0x01, 0x02)))
                                    .setIndex(1)
                            )
                            .setAmount(200_000L)
                    )
                    .build()
            },
        )

    private suspend fun assertAssetSideRefusedBy(ethInbound: THORChainInboundAddress) {
        givenNativeAccount(Chain.Ethereum, ETH_ADDRESS)
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns listOf(ethInbound)

        val text =
            assertFailsWith<InvalidTransactionDataException> {
                    build(Chain.Ethereum, pairedAddress = RUNE_ADDRESS, pool = ETH_POOL).build()
                }
                .text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.deposit_error_thorchain_chain_halted, text.resId)
    }

    private fun givenInboundSpecific(
        specific: BlockChainSpecific,
        utxos: List<UtxoInfo> = emptyList(),
    ) {
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
        } returns BlockChainSpecificAndUtxo(specific, utxos)
    }

    private fun inbound(
        chain: String,
        address: String,
        halted: Boolean = false,
        lpPaused: Boolean = false,
        tradingPaused: Boolean = false,
    ) =
        THORChainInboundAddress(
            chain = chain,
            address = address,
            halted = halted,
            chainLPActionsPaused = lpPaused,
            chainTradingPaused = tradingPaused,
            dustThreshold = "10000",
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

    private fun addressOf(chain: Chain): String =
        when (chain) {
            Chain.ThorChain -> RUNE_ADDRESS
            Chain.Ethereum -> ETH_ADDRESS
            else -> "maya1self"
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
        val ONE_COIN: BigInteger = BigInteger.TEN.pow(8)
        const val CACAO_ADDRESS = "maya1self"
        const val BTC_ADDRESS = "bc1qself"
        const val MAYA_BTC_POOL = "BTC.BTC"
        const val MAYA_USDC_POOL = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"
        const val ETH_POOL = "ETH.ETH"
        const val BTC_POOL = "BTC.BTC"
        const val ETH_INBOUND = "0xinbound"
        const val BTC_INBOUND = "bc1qinbound"
        const val PLAN_FEE = 2_500L
        // The plan's outpoint hash bytes, reversed into display order.
        const val PLANNED_UTXO_HASH = "0201"
    }
}
