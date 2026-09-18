@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapperImpl
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * A co-signed swap's history row records where its output went, so a retry pays the same address:
 * the recipient is read off the memo the device signs, which only the native routes carry. A route
 * that hides its destination in opaque bytes is recorded as unreadable instead, and never offers a
 * retry (#5918). Runs the real history mapper so the row, not a stub, is what is checked.
 */
internal class JoinSwapRecipientTest {

    private val tokenRepository: TokenRepository = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val getDiscountBps: GetDiscountBpsUseCase = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val swapQuoteRepository: SwapQuoteRepository = mockk()
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk()

    private val builder =
        JoinSwapUiModelBuilder(
            tokenRepository = tokenRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            feeServiceComposite = mockk(relaxed = true),
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            convertTokenValueToFiat = convertTokenValueToFiat,
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            swapQuoteRepository = swapQuoteRepository,
            mapSwapTransactionToHistoryData = SwapTransactionToHistoryDataMapperImpl(),
            formatLimitOrderLabels = FormatLimitOrderLabelsUseCase(),
            getDiscountBps = getDiscountBps,
        )

    @Test
    fun `a THORChain memo naming another address records it as the recipient`() = runTest {
        stub()

        val row = join(thorPayload(memo = "=:ETH.ETH:0xelse"), SwapPayload.ThorChain(thorSwap()))

        row.externalRecipient shouldBe "0xelse"
        row.isRecipientUnknown shouldBe false
    }

    @Test
    fun `a THORChain memo naming the vault records no recipient`() = runTest {
        stub()

        val row = join(thorPayload(memo = "=:ETH.ETH:0xdst"), SwapPayload.ThorChain(thorSwap()))

        row.externalRecipient.shouldBeNull()
        row.isRecipientUnknown shouldBe false
    }

    @Test
    fun `a native SwapKit route's recipient is unreadable`() = runTest {
        stub()

        val row = join(tronPayload(), SwapPayload.SwapKit(swapKitSwap()))

        row.externalRecipient.shouldBeNull()
        row.isRecipientUnknown shouldBe true
    }

    @Test
    fun `an EVM SwapKit route's recipient is unreadable too`() = runTest {
        stub()

        join(evmPayload(), evmSwap(SwapProvider.SWAPKIT)).isRecipientUnknown shouldBe true
    }

    @Test
    fun `an EVM aggregator route always pays the vault`() = runTest {
        // The aggregators bake the vault's address into calldata, and every platform drops them
        // the moment a recipient is set — so a row of theirs is known to be vault-bound.
        stub()

        join(evmPayload(), evmSwap(SwapProvider.KYBER)).isRecipientUnknown shouldBe false
    }

    private suspend fun join(
        payload: KeysignPayload,
        swapPayload: SwapPayload,
    ): SwapTransactionHistoryData =
        builder
            .build(payload = payload, swapPayload = swapPayload, vault = vault, currency = USD)
            .transactionHistoryData
            .shouldBeInstanceOf<SwapTransactionHistoryData>()

    private fun stub() {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { getDiscountBps(vault.id, any()) } returns 0
        coEvery { tokenRepository.getNativeToken(Chain.ThorChain.id) } returns rune
        coEvery { tokenRepository.getNativeToken(Chain.Tron.id) } returns trx
        coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns eth
        // The vault's own address on the destination chain, which the memo's is compared to.
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any()) } returns
            ("0xdst" to "")
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            SwapQuoteResult.Native(thorQuote())
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { fiatValueToStringMapper(any(), any()) } returns "0.00"
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "0.02",
                formattedFiatValue = "$0.20",
                tokenValue = TokenValue(BigInteger.valueOf(2_000_000L), rune),
                fiatValue = usd("0.20"),
            )
    }

    private fun thorPayload(memo: String) =
        KeysignPayload(
            coin = rune,
            toAddress = "thorInbound",
            toAmount = runeValue.value,
            memo = memo,
            blockChainSpecific =
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.valueOf(2_000_000L),
                    isDeposit = false,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private fun thorSwap() =
        THORChainSwapPayload(
            fromAddress = "thorsrc",
            fromCoin = rune,
            toCoin = eth,
            vaultAddress = "thorInbound",
            routerAddress = null,
            fromAmount = runeValue.value,
            toAmountDecimal = BigDecimal.ONE,
            toAmountLimit = "0",
            streamingInterval = "0",
            streamingQuantity = "0",
            expirationTime = 0UL,
            isAffiliate = true,
        )

    private fun thorQuote(): SwapQuote {
        val zero = TokenValue(BigInteger.ZERO, eth)
        return SwapQuote.ThorChain(
            expectedDstValue = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), eth),
            fees = zero,
            expiredAt = Clock.System.now() + 5.minutes,
            recommendedMinTokenValue = zero,
            data =
                THORChainSwapQuote(
                    dustThreshold = null,
                    expectedAmountOut = "1",
                    expiry = BigInteger.ZERO,
                    fees =
                        Fees(
                            affiliate = "60000000",
                            asset = "0",
                            outbound = "0",
                            total = "60000000",
                        ),
                    inboundAddress = "inbound",
                    inboundConfirmationBlocks = null,
                    inboundConfirmationSeconds = null,
                    maxStreamingQuantity = 0,
                    memo = "=:ETH.ETH:0xdst",
                    notes = "",
                    outboundDelayBlocks = BigInteger.ZERO,
                    outboundDelaySeconds = BigInteger.ZERO,
                    recommendedMinAmountIn = "0",
                    streamingSwapBlocks = BigInteger.ZERO,
                    totalSwapSeconds = 0L,
                    warning = "",
                    router = null,
                    error = null,
                ),
        )
    }

    private fun tronPayload() =
        KeysignPayload(
            coin = trx,
            toAddress = "Tdeposit",
            toAmount = trxValue.value,
            memo = null,
            blockChainSpecific =
                BlockChainSpecific.Tron(
                    timestamp = 0uL,
                    expiration = 0uL,
                    blockHeaderTimestamp = 0uL,
                    blockHeaderNumber = 0uL,
                    blockHeaderVersion = 0uL,
                    blockHeaderTxTrieRoot = "",
                    blockHeaderParentHash = "",
                    blockHeaderWitnessAddress = "",
                    gasFeeEstimation = 1_100_000uL,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private fun swapKitSwap() =
        SwapKitSwapPayloadJson(
            fromCoin = trx,
            toCoin = eth,
            fromAmount = trxValue.value,
            toAmountDecimal = BigDecimal("0.27"),
            txType = SwapKitSwapPayloadJson.TX_TYPE_TRON,
            txPayload = byteArrayOf(1),
            targetAddress = "Tdeposit",
            subProvider = "NEAR",
            // Stamped on the wire, so the builder has no fee to fetch for this route.
            swapFee = "13000000",
            swapFeeChain = Chain.Tron.id,
            swapFeeTokenId = null,
            swapFeeDecimals = 6,
        )

    private fun evmPayload() =
        KeysignPayload(
            coin = eth,
            toAddress = "0xRouter",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.valueOf(1_000_000_000L),
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.valueOf(100_000L),
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private fun evmSwap(provider: SwapProvider) =
        SwapPayload.EVM(
            EVMSwapPayloadJson(
                fromCoin = eth,
                toCoin = usdc,
                fromAmount = ethValue.value,
                toAmountDecimal = BigDecimal.ONE,
                quote =
                    EVMSwapQuoteJson(
                        dstAmount = "400",
                        tx =
                            OneInchSwapTxJson(
                                from = "0xdst",
                                to = "0xRouter",
                                gas = 100_000L,
                                data = "0xdata",
                                value = "0",
                                gasPrice = "1000000000",
                            ),
                    ),
                provider = provider.getSwapProviderId(),
            )
        )

    private fun usd(amount: String) = FiatValue(BigDecimal(amount), "USD")

    private val rune =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "",
            address = "thorsrc",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        )

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xdst",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private val usdc =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xdst",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "usd-coin",
            contractAddress = "0xusdc",
            isNativeToken = false,
        )

    private val trx =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = "Tsrc",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tron",
            contractAddress = "",
            isNativeToken = true,
        )

    private val runeValue = TokenValue(BigInteger.valueOf(100_000_000L), rune)
    private val trxValue = TokenValue(BigInteger.valueOf(2_000_000_000L), trx)
    private val ethValue = TokenValue(BigInteger.valueOf(100_000_000_000_000_000L), eth)

    private val vault =
        Vault(id = "vault-1", name = "Main", pubKeyECDSA = "pub", pubKeyEDDSA = "pubed")

    private companion object {
        val USD = AppCurrency.USD
    }
}
