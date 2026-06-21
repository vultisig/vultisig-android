package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.models.swapAssetComparisonName
import com.vultisig.wallet.data.models.swapProviderFromWireId
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.swap.formatSwapKitProviderLabel
import com.vultisig.wallet.ui.models.swap.resolveExternalSwapRecipient
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Builds the [VerifyUiModel.Swap] model for the join-keysign verify screen. Extracted verbatim from
 * `JoinKeysignViewModel.loadTransaction`'s swap branch (including its per-provider sub-branches and
 * the shared [buildSwapUiModel]) — behavior is unchanged.
 */
internal class JoinSwapUiModelBuilder
@Inject
constructor(
    private val tokenRepository: TokenRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val swapQuoteRepository: SwapQuoteRepository,
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper,
) {

    /**
     * Builds the swap [JoinKeysignVerifyResult] from [payload] and [swapPayload] for [vault],
     * computing network/provider fees across providers and chains.
     *
     * @param currency fiat currency used for value conversion.
     */
    suspend fun build(
        payload: KeysignPayload,
        swapPayload: SwapPayload,
        vault: Vault,
        currency: AppCurrency,
    ): JoinKeysignVerifyResult {
        val srcToken = swapPayload.srcToken
        val dstToken = swapPayload.dstToken

        val srcTokenValue = swapPayload.srcTokenValue
        val dstTokenValue = swapPayload.dstTokenValue

        val nativeToken = tokenRepository.getNativeToken(srcToken.chain.id)

        val chain = srcToken.chain
        val blockChainSpecific = payload.blockChainSpecific
        val gasFee =
            when {
                chain.standard == TokenStandard.UTXO && chain != Chain.Cardano -> {
                    val utxoHelper = UtxoHelper.getHelper(vault, srcToken.coinType)
                    val plan = utxoHelper.getBitcoinTransactionPlan(payload)
                    if (plan.error != wallet.core.jni.proto.Common.SigningError.OK) {
                        Timber.e("UTXO plan error: ${plan.error.name}")
                    }
                    TokenValue(value = BigInteger.valueOf(plan.fee), token = nativeToken)
                }
                blockChainSpecific is BlockChainSpecific.Ethereum ||
                    blockChainSpecific is BlockChainSpecific.THORChain ->
                    computeJoinKeysignSwapNetworkFee(
                        blockChainSpecific = blockChainSpecific,
                        nativeCoin = nativeToken,
                    )
                else -> {
                    val (nativeTokenAddress, _) =
                        chainAccountAddressRepository.getAddress(nativeToken, vault)
                    val blockchainTransaction =
                        Swap(
                            coin = srcToken,
                            vault =
                                VaultData(
                                    vaultHexPublicKey = vault.getPubKeyByChain(chain),
                                    vaultHexChainCode = vault.hexChainCode,
                                ),
                            amount = swapPayload.srcTokenValue.value,
                            to = nativeTokenAddress,
                            callData = "",
                            approvalData = null,
                        )
                    val calculatedFee =
                        withContext(Dispatchers.IO) {
                            feeServiceComposite.calculateFees(blockchainTransaction)
                        }
                    TokenValue(value = calculatedFee.amount, token = nativeToken)
                }
            }
        val estimatedNetworkGasFee: EstimatedGasFee =
            gasFeeToEstimatedFee(
                GasFeeParams(
                    gasLimit = BigInteger.valueOf(1),
                    gasFee = gasFee,
                    selectedToken = srcToken,
                )
            )
        val networkGasFeeFiatValue = estimatedNetworkGasFee.fiatValue

        val vaultName = vault.name

        val provider =
            when (swapPayload) {
                is SwapPayload.ThorChain -> SwapProvider.THORCHAIN.getSwapProviderId()
                is SwapPayload.MayaChain -> SwapProvider.MAYA.getSwapProviderId()
                is SwapPayload.EVM ->
                    swapProviderFromWireId(swapPayload.data.provider)?.getSwapProviderId()
                        ?: swapPayload.data.provider
                is SwapPayload.SwapKit -> SwapProvider.SWAPKIT.getSwapProviderId()
            }

        // Display-only label. `provider` above stays the canonical id (the behavioral key
        // that gates SwapKit `/track` settlement); native SwapKit routes render their
        // sub-provider (`SwapKit (NEAR)`). All other providers reuse the canonical id.
        val providerLabel =
            when (swapPayload) {
                is SwapPayload.SwapKit -> formatSwapKitProviderLabel(swapPayload.data.subProvider)
                else -> provider
            }

        // Surface a custom recipient on the cosigner's verify screen too — null for routes that
        // don't carry one (#4858 review). Reused across the provider branches below.
        val externalRecipient = resolveExternalRecipient(payload, swapPayload, dstToken, vault)

        return when (swapPayload) {
            is SwapPayload.EVM -> {
                val oneInchSwapTxJson = swapPayload.data.quote.tx
                val hasJupiterSwapProvider =
                    srcToken.chain == Chain.Solana && dstToken.chain == Chain.Solana
                // LI.FI and SwapKit both produce cross-chain EVM swaps. Detect LI.FI by
                // wire id first; fall back to the cross-chain heuristic only for
                // non-SwapKit
                // providers so a SwapKit Ethereum→Solana swap reads `swapFee` instead of
                // the
                // LI.FI integrator-fee formula.
                val isLiFi =
                    provider == SwapProvider.LIFI.getSwapProviderId() ||
                        (srcToken.chain != dstToken.chain &&
                            provider != SwapProvider.SWAPKIT.getSwapProviderId())

                // When the initiator stamped the swap-fee coin context (commondata
                // swap_fee_chain / swap_fee_token_id / swap_fee_decimals, surfaced here as
                // swapFeeChain / swapFeeTokenContract / swapFeeDecimals), trust it instead
                // of guessing the fee coin. This is what lets a payload whose affiliate fee
                // is in the destination token (e.g. KyberSwap USDC, 6 decimals) render the
                // right fiat value rather than misreading a 6-decimal amount as 18-decimal
                // native and overshooting by ~10^12. Empty chain / null decimals means a
                // sender that predates the field, so we fall back to the heuristic below.
                val explicitSwapFee: Pair<Coin, BigInteger>? = run {
                    val feeChain = oneInchSwapTxJson.swapFeeChain
                    val feeRaw = oneInchSwapTxJson.swapFee.toBigIntegerOrNull()
                    if (
                        feeChain.isEmpty() ||
                            oneInchSwapTxJson.swapFeeDecimals == null ||
                            feeRaw == null
                    ) {
                        return@run null
                    }
                    val feeTokenId = oneInchSwapTxJson.swapFeeTokenContract
                    val coin =
                        listOf(dstToken, srcToken, nativeToken).firstOrNull {
                            it.chain.id == feeChain &&
                                it.contractAddress.equals(feeTokenId, ignoreCase = true)
                        } ?: return@run null
                    coin to feeRaw
                }

                val feeToken =
                    explicitSwapFee?.first
                        ?: when {
                            // Mirror iOS / SwapFormViewModel: LI.FI integrator fee is a
                            // percentage of the destination amount, denominated in the
                            // destination token. The raw "LIFI Fixed Fee" amount has no
                            // chainId and cannot be safely interpreted as source-native wei
                            // for cross-chain swaps (#3300).
                            isLiFi -> dstToken
                            hasJupiterSwapProvider -> srcToken
                            else -> nativeToken
                        }

                val value =
                    when {
                        explicitSwapFee != null -> explicitSwapFee.second
                        // VULT tier discount isn't available in the join flow, so this
                        // uses the base integrator rate. The difference vs. the initiator
                        // display is at most 0.5% of dstAmount.
                        isLiFi -> LiFiChainApi.integratorFeeAmount(dstAmount = dstTokenValue.value)
                        oneInchSwapTxJson.swapFee.isNotEmpty() &&
                            oneInchSwapTxJson.swapFee.toBigIntegerOrNull() != null ->
                            oneInchSwapTxJson.swapFee.toBigInteger()
                        else ->
                            (oneInchSwapTxJson.gasPrice.toBigIntegerOrNull() ?: BigInteger.ZERO) *
                                (oneInchSwapTxJson.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                                    .toBigInteger()
                    }

                val estimatedTokenFees = TokenValue(value = value, token = feeToken)

                val estimatedFee = convertTokenValueToFiat(feeToken, estimatedTokenFees, currency)

                val swapTransaction =
                    SwapTransactionUiModel(
                        src =
                            ValuedToken(
                                value = mapTokenValueToDecimalUiString(srcTokenValue),
                                token = srcToken,
                                fiatValue =
                                    fiatValueToStringMapper(
                                        convertTokenValueToFiat(srcToken, srcTokenValue, currency)
                                    ),
                            ),
                        dst =
                            ValuedToken(
                                value = mapTokenValueToDecimalUiString(dstTokenValue),
                                token = dstToken,
                                fiatValue =
                                    fiatValueToStringMapper(
                                        convertTokenValueToFiat(dstToken, dstTokenValue, currency)
                                    ),
                            ),
                        providerFee =
                            ValuedToken(
                                token = feeToken,
                                value = value.toString(),
                                fiatValue = fiatValueToStringMapper(estimatedFee, asFee = true),
                            ),
                        networkFee =
                            ValuedToken(
                                token = srcToken,
                                value =
                                    mapTokenValueToDecimalUiString(
                                        estimatedNetworkGasFee.tokenValue
                                    ),
                                fiatValue =
                                    fiatValueToStringMapper(
                                        estimatedNetworkGasFee.fiatValue,
                                        asFee = true,
                                    ),
                            ),
                        networkFeeFormatted =
                            mapTokenValueToDecimalUiString(estimatedNetworkGasFee.tokenValue) +
                                " ${estimatedNetworkGasFee.tokenValue.unit}",
                        totalFee =
                            fiatValueToStringMapper(
                                estimatedFee + networkGasFeeFiatValue,
                                asFee = true,
                            ),
                        provider = provider,
                    )

                JoinKeysignVerifyResult(
                    verifyUiModel =
                        VerifyUiModel.Swap(
                            VerifySwapUiModel(tx = swapTransaction, vaultName = vaultName)
                        ),
                    transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransaction),
                    transactionHistoryData = mapSwapTransactionToHistoryData(swapTransaction),
                )
            }

            is SwapPayload.ThorChain -> {
                if (srcToken.swapAssetComparisonName() == dstToken.swapAssetComparisonName()) {
                    val lpAddUiModel =
                        buildSwapUiModel(
                            srcToken = srcToken,
                            srcTokenValue = srcTokenValue,
                            dstToken = dstToken,
                            dstTokenValue = dstTokenValue,
                            estimatedNetworkGasFee = estimatedNetworkGasFee,
                            provider = provider,
                            providerFee = TokenValue(value = BigInteger.ZERO, token = srcToken),
                            providerFeeToken = srcToken,
                            currency = currency,
                        )
                    return JoinKeysignVerifyResult(
                        verifyUiModel =
                            VerifyUiModel.Swap(
                                VerifySwapUiModel(tx = lpAddUiModel, vaultName = vaultName)
                            ),
                        transactionTypeUiModel = TransactionTypeUiModel.Swap(lpAddUiModel),
                        transactionHistoryData = mapSwapTransactionToHistoryData(lpAddUiModel),
                    )
                }
                val quote =
                    swapQuoteRepository
                        .getQuote(
                            SwapProvider.THORCHAIN,
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = srcTokenValue,
                                dstAddress = swapPayload.data.toAddress,
                            ),
                        )
                        .expectNative(SwapProvider.THORCHAIN)
                val swapTransactionUiModel =
                    buildSwapUiModel(
                        srcToken = srcToken,
                        srcTokenValue = srcTokenValue,
                        dstToken = dstToken,
                        dstTokenValue = dstTokenValue,
                        estimatedNetworkGasFee = estimatedNetworkGasFee,
                        provider = provider,
                        providerFee = quote.fees,
                        providerFeeToken = dstToken,
                        currency = currency,
                        externalRecipient = externalRecipient,
                    )
                JoinKeysignVerifyResult(
                    verifyUiModel =
                        VerifyUiModel.Swap(
                            VerifySwapUiModel(tx = swapTransactionUiModel, vaultName = vaultName)
                        ),
                    transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransactionUiModel),
                    transactionHistoryData = mapSwapTransactionToHistoryData(swapTransactionUiModel),
                )
            }

            is SwapPayload.MayaChain -> {
                if (srcToken.swapAssetComparisonName() == dstToken.swapAssetComparisonName()) {
                    val lpAddUiModel =
                        buildSwapUiModel(
                            srcToken = srcToken,
                            srcTokenValue = srcTokenValue,
                            dstToken = dstToken,
                            dstTokenValue = dstTokenValue,
                            estimatedNetworkGasFee = estimatedNetworkGasFee,
                            provider = provider,
                            providerFee = TokenValue(value = BigInteger.ZERO, token = srcToken),
                            providerFeeToken = srcToken,
                            currency = currency,
                        )
                    return JoinKeysignVerifyResult(
                        verifyUiModel =
                            VerifyUiModel.Swap(
                                VerifySwapUiModel(tx = lpAddUiModel, vaultName = vaultName)
                            ),
                        transactionTypeUiModel = TransactionTypeUiModel.Swap(lpAddUiModel),
                        transactionHistoryData = mapSwapTransactionToHistoryData(lpAddUiModel),
                    )
                }
                val quote =
                    swapQuoteRepository
                        .getQuote(
                            SwapProvider.MAYA,
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = srcTokenValue,
                                dstAddress = swapPayload.data.toAddress,
                                isAffiliate = true,
                            ),
                        )
                        .expectNative(SwapProvider.MAYA)
                val swapTransactionUiModel =
                    buildSwapUiModel(
                        srcToken = srcToken,
                        srcTokenValue = srcTokenValue,
                        dstToken = dstToken,
                        dstTokenValue = dstTokenValue,
                        estimatedNetworkGasFee = estimatedNetworkGasFee,
                        provider = provider,
                        providerFee = quote.fees,
                        providerFeeToken = dstToken,
                        currency = currency,
                        externalRecipient = externalRecipient,
                    )
                JoinKeysignVerifyResult(
                    verifyUiModel =
                        VerifyUiModel.Swap(
                            VerifySwapUiModel(tx = swapTransactionUiModel, vaultName = vaultName)
                        ),
                    transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransactionUiModel),
                    transactionHistoryData = mapSwapTransactionToHistoryData(swapTransactionUiModel),
                )
            }

            is SwapPayload.SwapKit -> {
                // Re-fetch only the SwapKit inbound fee at join time so the co-signer sees
                // the same non-zero swap fee as the initiator (mirrors the Thor/Maya
                // branches). Uses the quote-only `getSwapKitInboundFee` (`POST /v3/quote`)
                // rather than the full `getQuote`, which would also fire `POST /v3/swap`
                // and
                // mint a throwaway swap route — a fresh `swapId` and deposit address — per
                // cosigner just to read the fee. Display-only: the signing bytes come from
                // the payload and are never touched. The quote may reprice slightly between
                // fetches (approximate parity, the same trade-off Thor/Maya accept); a
                // fetch
                // failure degrades to a zero fee rather than stalling the verify screen.
                // The
                // initiator's `affiliateBps` / `srcAddress` are intentionally omitted — the
                // inbound fee doesn't depend on the affiliate fee and the join device can't
                // know the initiator's `vultBPSDiscount`, so approximate parity holds.
                val swapKitProviderFee =
                    try {
                        swapQuoteRepository.getSwapKitInboundFee(
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = srcTokenValue,
                                dstAddress = dstToken.address,
                            )
                        )
                    } catch (e: SwapKitError) {
                        // The SwapKit API layer wraps network/timeout/decoding failures
                        // into
                        // SwapKitError and rethrows CancellationException un-wrapped, so
                        // this
                        // narrow catch degrades quote failures to a zero fee while letting
                        // cancellation (and any genuinely unexpected exception) propagate.
                        Timber.w(e, "SwapKit join fee re-fetch failed; showing zero fee")
                        TokenValue(value = BigInteger.ZERO, token = srcToken)
                    }
                val swapTransactionUiModel =
                    buildSwapUiModel(
                        srcToken = srcToken,
                        srcTokenValue = srcTokenValue,
                        dstToken = dstToken,
                        dstTokenValue = dstTokenValue,
                        estimatedNetworkGasFee = estimatedNetworkGasFee,
                        provider = provider,
                        providerFee = swapKitProviderFee,
                        providerFeeToken = srcToken,
                        currency = currency,
                        providerLabel = providerLabel,
                    )
                JoinKeysignVerifyResult(
                    verifyUiModel =
                        VerifyUiModel.Swap(
                            VerifySwapUiModel(tx = swapTransactionUiModel, vaultName = vaultName)
                        ),
                    transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransactionUiModel),
                    transactionHistoryData = mapSwapTransactionToHistoryData(swapTransactionUiModel),
                )
            }
        }
    }

    private suspend fun buildSwapUiModel(
        srcToken: Coin,
        srcTokenValue: TokenValue,
        dstToken: Coin,
        dstTokenValue: TokenValue,
        estimatedNetworkGasFee: EstimatedGasFee,
        provider: String,
        providerFee: TokenValue,
        providerFeeToken: Coin,
        currency: AppCurrency,
        providerLabel: String = provider,
        externalRecipient: String? = null,
    ): SwapTransactionUiModel {
        val estimatedFee = convertTokenValueToFiat(providerFeeToken, providerFee, currency)
        return SwapTransactionUiModel(
            src =
                ValuedToken(
                    value = mapTokenValueToDecimalUiString(srcTokenValue),
                    token = srcToken,
                    fiatValue =
                        fiatValueToStringMapper(
                            convertTokenValueToFiat(srcToken, srcTokenValue, currency)
                        ),
                ),
            dst =
                ValuedToken(
                    value = mapTokenValueToDecimalUiString(dstTokenValue),
                    token = dstToken,
                    fiatValue =
                        fiatValueToStringMapper(
                            convertTokenValueToFiat(dstToken, dstTokenValue, currency)
                        ),
                ),
            networkFee =
                ValuedToken(
                    token = srcToken,
                    value = mapTokenValueToDecimalUiString(estimatedNetworkGasFee.tokenValue),
                    fiatValue =
                        fiatValueToStringMapper(estimatedNetworkGasFee.fiatValue, asFee = true),
                ),
            providerFee =
                ValuedToken(
                    token = providerFeeToken,
                    value = providerFee.value.toString(),
                    fiatValue = fiatValueToStringMapper(estimatedFee, asFee = true),
                ),
            networkFeeFormatted =
                mapTokenValueToDecimalUiString(estimatedNetworkGasFee.tokenValue) +
                    " ${estimatedNetworkGasFee.tokenValue.unit}",
            totalFee =
                fiatValueToStringMapper(
                    estimatedFee + estimatedNetworkGasFee.fiatValue,
                    asFee = true,
                ),
            provider = provider,
            providerLabel = providerLabel,
            externalRecipient = externalRecipient,
        )
    }

    /**
     * Recovers the custom external recipient a cosigner would otherwise never see (#4858
     * review, #4972). Only the native protocols (THORChain / Maya) carry it, as the swap memo's
     * `destination` segment; a value matching the vault's own destination address is the normal
     * case, so only a differing address is surfaced. Parsing and the chain-aware own-address
     * comparison are delegated to the unit-tested [resolveExternalSwapRecipient]. Returns null when
     * it can't be determined (EVM aggregators bake the vault address into calldata; SwapKit's route
     * isn't recoverable here).
     */
    private suspend fun resolveExternalRecipient(
        payload: KeysignPayload,
        swapPayload: SwapPayload,
        dstToken: Coin,
        vault: Vault,
    ): String? {
        if (swapPayload !is SwapPayload.ThorChain && swapPayload !is SwapPayload.MayaChain) {
            return null
        }
        val vaultDstAddress =
            runCatching { chainAccountAddressRepository.getAddress(dstToken, vault).first }
                .getOrNull() ?: return null
        return resolveExternalSwapRecipient(
            memo = payload.memo,
            destinationChain = dstToken.chain,
            vaultDestinationAddress = vaultDstAddress,
        )
    }
}
