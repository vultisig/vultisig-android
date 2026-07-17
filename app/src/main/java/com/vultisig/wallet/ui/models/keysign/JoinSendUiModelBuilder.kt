package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.RippleDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ContractAbiRepository
import com.vultisig.wallet.data.repositories.PrettyJson
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.utils.resolveDstVaultName
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import wallet.core.jni.TONAddressConverter
import wallet.core.jni.proto.Common.SigningError

/**
 * Builds the [VerifyUiModel.Send] model for the join-keysign verify screen. Extracted verbatim from
 * `JoinKeysignViewModel.loadTransaction`'s send branch — behavior is unchanged. The background
 * hero/scan enrichment that the send branch kicks off stays in the ViewModel (it launches into
 * `viewModelScope`); this builder returns the [Transaction] and decoded function name those jobs
 * need via [JoinSendUiModelResult].
 */
internal class JoinSendUiModelBuilder
@Inject
constructor(
    private val tokenRepository: TokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val vaultRepository: VaultRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
    private val mapTransactionHistoryData: SendTransactionHistoryDataMapper,
    private val addressBookRepository: AddressBookRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenMetadataResolver: TokenMetadataResolver,
    private val contractAbiRepository: ContractAbiRepository,
    private val parseCosmosMessage: ParseCosmosMessageUseCase,
    private val feeResolver: JoinKeysignFeeResolver,
    @param:PrettyJson private val json: Json,
) {

    /**
     * Builds the send [JoinSendUiModelResult] from [payload] for the vault identified by [vaultId],
     * resolving gas/fees and decoding any EVM calldata. Returns null when the model cannot be
     * built.
     *
     * @param srcVaultName display name of the sending vault.
     * @param currency fiat currency used for value conversion.
     */
    suspend fun build(
        payload: KeysignPayload,
        srcVaultName: String,
        vaultId: String,
        currency: AppCurrency,
    ): JoinSendUiModelResult? {
        val payloadToken = payload.coin
        val address = payloadToken.address
        val chain = payloadToken.chain

        val tokenValue =
            TokenValue(
                value = payload.toAmount,
                unit = payloadToken.ticker,
                decimals = payloadToken.decimal,
            )

        val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: return null

        val blockchainTransaction =
            Transfer(
                coin = payloadToken,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = tokenValue.value,
                to = payload.toAddress,
                memo = payload.memo,
                isMax = false,
            )

        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }

        // Force a live price refresh for the sent token and the chain's native fee token before
        // converting to fiat. The initiator prices these from its freshly-refreshed portfolio rate,
        // while a joining device would otherwise read whatever price is already persisted locally —
        // `getPrice` only re-fetches when the cached value is exactly zero, so a stale-but-nonzero
        // cached rate (seen on DYDX) makes the "You're sending" and "Network Fee" fiat totals
        // differ between devices for identical crypto amounts. Refreshing here pins both devices
        // to the same shared source (CoinGecko via the payload's priceProviderID) so the fiat
        // values converge.
        withContext(Dispatchers.IO) {
            runCatching { tokenPriceRepository.refresh(listOf(payloadToken, nativeCoin)) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.w(it, "Failed to refresh price for %s", chain.id)
                }
        }
        // A dApp XRPL tx is signed verbatim, so the fee that is actually paid is the `Fee` baked
        // into its raw JSON — not a live re-estimate. Surface that exact value so an inflated Fee
        // is
        // visible on the co-signer's Verify screen instead of being masked by a normal-looking
        // RippleFeeService estimate.
        val rippleDappFeeDrops =
            payload.signRipple?.rawJson?.let { RippleDappTransactionDecoder.feeDrops(it) }
        val gasFee =
            when {
                chain.standard == TokenStandard.UTXO && chain != Chain.Cardano -> {
                    val utxoHelper = UtxoHelper.getHelper(vault, payloadToken.coinType)
                    val plan = utxoHelper.getBitcoinTransactionPlan(payload)
                    if (plan.error != SigningError.OK) {
                        Timber.e("UTXO plan error: ${plan.error.name}")
                    }
                    TokenValue(value = BigInteger.valueOf(plan.fee), token = nativeCoin)
                }

                rippleDappFeeDrops != null ->
                    TokenValue(value = rippleDappFeeDrops, token = nativeCoin)

                else ->
                    feeResolver.resolveJoinKeysignNetworkFee(
                        payload = payload,
                        chain = chain,
                        nativeCoin = nativeCoin,
                        blockchainTransaction = blockchainTransaction,
                    )
            }

        val totalGasAndFee =
            gasFeeToEstimatedFee(
                GasFeeParams(
                    gasLimit = BigInteger.valueOf(1),
                    gasFee = gasFee,
                    selectedToken = payload.coin,
                )
            )
        val functionInfo = feeResolver.getTransactionFunctionInfo(payload.memo, chain)
        val normalizedSignAminoJson =
            kotlinx.serialization.json.buildJsonArray {
                payload.signAmino?.msgs?.forEach { cosmosMsg ->
                    cosmosMsg?.type ?: return@forEach
                    addJsonObject {
                        val type = cosmosMsg.type
                        val valueElem =
                            try {
                                json.parseToJsonElement(cosmosMsg.value)
                            } catch (e: Exception) {
                                kotlinx.serialization.json.JsonPrimitive(cosmosMsg.value)
                            }

                        put("type", kotlinx.serialization.json.JsonPrimitive(type))
                        put("value", valueElem)
                    }
                }
            }

        val normalizedSignAmino =
            json.encodeToString(normalizedSignAminoJson).takeIf {
                !normalizedSignAminoJson.isEmpty()
            } ?: ""
        val signDirect =
            payload.signDirect?.let { json.encodeToString(parseCosmosMessage(it)) } ?: ""

        val signSolana = payload.signSolana?.rawTransactions?.firstOrNull() ?: ""
        val signSui = payload.signSui?.unsignedTxMsg?.takeIf { it.isNotEmpty() }
        val signRipple = payload.signRipple?.rawJson?.takeIf { it.isNotBlank() }
        val transaction =
            Transaction(
                id = UUID.randomUUID().toString(),
                vaultId = payload.vaultPublicKeyECDSA,
                chainId = chain.id,
                token = payloadToken,
                srcAddress = address,
                dstAddress = payload.toAddress,
                tokenValue = tokenValue,
                fiatValue = convertTokenValueToFiat(payloadToken, tokenValue, currency),
                gasFee = gasFee,
                memo = payload.memo.takeIf { functionInfo == null },
                estimatedFee = totalGasAndFee.formattedFiatValue,
                blockChainSpecific = payload.blockChainSpecific,
                totalGas = totalGasAndFee.formattedTokenValue,
                signAmino = normalizedSignAmino,
                signDirect = signDirect,
                signSolana = signSolana,
                signSui = signSui,
                signRipple = signRipple,
            )

        val transactionToUiModel = mapTransactionToUiModel(transaction)

        val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
        val dstVaultName =
            resolveDstVaultName(
                allVaults = allVaults,
                chain = chain,
                dstAddress = payload.toAddress,
                chainAccountAddressRepository = chainAccountAddressRepository,
            )
        val dstAddressBookTitle =
            if (dstVaultName == null) {
                addressBookRepository.getEntry(chain.id, payload.toAddress)?.title
            } else null

        val isUnlimitedApproval =
            functionInfo != null &&
                isUnlimitedApproval(functionInfo.signature, functionInfo.inputs, json)
        val approvalSpender =
            if (isUnlimitedApproval) {
                val spenderIdx = approvalSpenderArgIndex(functionInfo?.signature ?: "")
                if (spenderIdx != null) {
                    runCatching {
                            json
                                .parseToJsonElement(functionInfo?.inputs ?: "[]")
                                .jsonArray
                                .getOrNull(spenderIdx)
                                ?.jsonPrimitive
                                ?.content
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                        }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                } else null
            } else null
        val decodedExtras =
            enrichDecodedCall(
                chain = chain,
                dstAddress = payload.toAddress,
                functionInfo = functionInfo,
                allVaults = allVaults,
                isUnlimitedApproval = isUnlimitedApproval,
                json = json,
                tokenMetadataResolver = tokenMetadataResolver,
                nativeTokenLookup = { c -> nativeTokenOrNull(c.id) },
                resolveAbiParams = { c, abiAddress, sig ->
                    contractAbiRepository.resolveParams(c, abiAddress, sig)
                },
            )

        val tonMessages =
            mapTonMessages(payload.signTon, fromAddress = payload.coin.address) { rawAddress ->
                TONAddressConverter.toUserFriendly(rawAddress, true, false) ?: rawAddress
            }
        val namedTransactionUiModel =
            transactionToUiModel.copy(
                srcVaultName = srcVaultName,
                dstVaultName = dstVaultName,
                dstAddressBookTitle = dstAddressBookTitle,
                functionSignature = functionInfo?.signature,
                functionInputs = functionInfo?.inputs,
                functionName = functionInfo?.functionName,
                isUnlimitedApproval = isUnlimitedApproval,
                approvalSpender = approvalSpender,
                approvalTokenTicker = decodedExtras.approvalTokenTicker,
                dstContractLabel = decodedExtras.dstContractLabel,
                decodedFunctionParams = decodedExtras.decodedFunctionParams,
                isUniversalRouterSwap = decodedExtras.isUniversalRouterSwap,
                tonMessages = tonMessages,
            )
        return JoinSendUiModelResult(
            result =
                JoinKeysignVerifyResult(
                    verifyUiModel =
                        VerifyUiModel.Send(
                            VerifyTransactionUiModel(transaction = namedTransactionUiModel)
                        ),
                    transactionTypeUiModel = TransactionTypeUiModel.Send(namedTransactionUiModel),
                    transactionHistoryData = mapTransactionHistoryData(namedTransactionUiModel),
                ),
            transaction = transaction,
            functionName = functionInfo?.functionName,
            vaultCoins = vault.coins,
        )
    }

    /**
     * Fetches the chain's native coin for the Universal Router swap-intent decoder so a native-ETH
     * leg renders the right ticker. Non-fatal — a failed RPC just means the row displays the bare
     * zero address. [CancellationException] propagates so structured-concurrency cancellation isn't
     * swallowed.
     */
    private suspend fun nativeTokenOrNull(chainId: String) =
        try {
            tokenRepository.getNativeToken(chainId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve native token for %s", chainId)
            null
        }
}
