@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.RippleDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.SubstrateDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.cosmosNativeDenom
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.substrateDappPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ContractAbiRepository
import com.vultisig.wallet.data.repositories.PrettyJson
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.transaction.VerifyTransactionUiModel
import com.vultisig.wallet.ui.utils.resolveDstVaultName
import java.math.BigInteger
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

        // A Substrate dApp call is signed as the bytes in the memo, so the recipient and amount
        // shown here are read out of those bytes (a Balances transfer) or not shown at all; the
        // wire `toAddress` / `toAmount` are the initiator's display copy and are never trusted.
        val substrateDapp = payload.substrateDappPayload
        val substrateTransfer =
            substrateDapp
                ?.let {
                    SubstrateDappTransactionDecoder.decode(
                        payload = it,
                        chain = chain,
                        decimals = payloadToken.decimal,
                        ticker = payloadToken.ticker,
                    )
                }
                ?.transfer

        // A Cosmos `signDirect` request is signed as the SignDoc bytes, so the recipient and
        // amount must be read out of those bytes — never from the wire `toAddress` / `toAmount`,
        // which are the initiator's unverified display copy. A compromised initiator could
        // otherwise show the co-signer one recipient while the signed body pays another. When the
        // body isn't a single bank `MsgSend` (staking, multi-message, dApp calls) no simple To/
        // amount is shown; the full decoded SignDoc is rendered below in the Direct Sign card.
        val signDirectMessage = payload.signDirect?.let { parseCosmosMessage(it) }
        // The signed coin's denom has to match what the row will be labelled with, or the signed
        // integer would render under the wrong ticker/decimals. Native sends carry the chain's
        // native denom; other Cosmos assets carry their denom in the coin's contract address.
        val expectedSignDirectDenom =
            if (payloadToken.isNativeToken) chain.cosmosNativeDenom else payloadToken.contractAddress
        val signDirectTransfer = signDirectMessage?.singleMsgSendTransfer(expectedSignDirectDenom)

        val dstAddress =
            when {
                substrateDapp != null -> substrateTransfer?.recipient.orEmpty()
                payload.signDirect != null -> signDirectTransfer?.recipient.orEmpty()
                else -> payload.toAddress
            }
        val amount =
            when {
                substrateDapp != null -> substrateTransfer?.amount ?: BigInteger.ZERO
                payload.signDirect != null -> signDirectTransfer?.amount ?: BigInteger.ZERO
                else -> payload.toAmount
            }

        val tokenValue =
            TokenValue(value = amount, unit = payloadToken.ticker, decimals = payloadToken.decimal)

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
                to = dstAddress,
                memo = payload.memo,
                isMax = false,
            )

        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
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

                // The fee service would rebuild a transfer around the payload's (empty) toAddress
                // to estimate. The initiator already put its Substrate fee estimate in `gas`.
                substrateDapp != null ->
                    TokenValue(
                        value =
                            (payload.blockChainSpecific as? BlockChainSpecific.Polkadot)
                                ?.gas
                                ?.toString()
                                ?.toBigInteger() ?: BigInteger.ZERO,
                        token = nativeCoin,
                    )

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
        // A Tron dApp contract call is signed from its `data`, not from the memo, so that is what
        // gets decoded; otherwise an `approve` would render as a plain 0 TRX send to the contract.
        val calldata =
            if (chain == Chain.Tron) payload.tronTriggerSmartContractPayload?.data
            else payload.memo
        val functionInfo = feeResolver.getTransactionFunctionInfo(calldata, chain)
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
        val signDirect = signDirectMessage?.let { json.encodeToString(it) } ?: ""

        val signSolana = payload.signSolana?.rawTransactions.orEmpty()
        val signSui = payload.signSui?.unsignedTxMsg?.takeIf { it.isNotEmpty() }
        val signRipple = payload.signRipple?.rawJson?.takeIf { it.isNotBlank() }
        val transaction =
            Transaction(
                id = Uuid.random().toString(),
                vaultId = payload.vaultPublicKeyECDSA,
                chainId = chain.id,
                token = payloadToken,
                srcAddress = address,
                dstAddress = dstAddress,
                tokenValue = tokenValue,
                fiatValue = convertTokenValueToFiat(payloadToken, tokenValue, currency),
                gasFee = gasFee,
                // A Substrate signer payload is the signed content, rendered by its own card, not
                // a memo.
                memo =
                    payload.memo.takeIf {
                        (functionInfo == null || chain == Chain.Tron) && substrateDapp == null
                    },
                estimatedFee = totalGasAndFee.formattedFiatValue,
                blockChainSpecific = payload.blockChainSpecific,
                totalGas = totalGasAndFee.formattedTokenValue,
                signAmino = normalizedSignAmino,
                signDirect = signDirect,
                signSolana = signSolana,
                signSui = signSui,
                signRipple = signRipple,
                signSubstrate = substrateDapp?.rawJson,
            )

        val transactionToUiModel = mapTransactionToUiModel(transaction)

        val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
        val dstVaultName =
            resolveDstVaultName(
                allVaults = allVaults,
                chain = chain,
                dstAddress = dstAddress,
                chainAccountAddressRepository = chainAccountAddressRepository,
            )
        val dstAddressBookTitle =
            if (dstVaultName == null) {
                addressBookRepository.getEntry(chain.id, dstAddress)?.title
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
                dstAddress = dstAddress,
                // Token, label and ABI lookups are EVM-only.
                functionInfo = functionInfo?.takeIf { chain.standard == TokenStandard.EVM },
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

    /**
     * Pulls the recipient and amount out of a decoded `signDirect` SignDoc, but only when the body
     * is exactly one bank `MsgSend` carrying a single coin whose denom is [expectedDenom]. That is
     * the one case where a simple "To / amount" row can faithfully stand in for the signed bytes.
     * Anything else — staking, governance, multi-message or dApp bodies, or a coin in a denom that
     * doesn't match what the row would be labelled with — returns null so the verify screen shows
     * no spoofable To/amount and relies on the full Direct Sign card instead. The recipient and
     * amount are taken verbatim from the signed bytes, so what the co-signer sees is what it signs.
     */
    private fun CosmosMessage.singleMsgSendTransfer(expectedDenom: String?): SignDirectTransfer? {
        if (expectedDenom.isNullOrEmpty()) return null
        val message = messages.singleOrNull() ?: return null
        if (message.typeUrl != MSG_SEND_TYPE_URL) return null
        val body = message.value as? JsonObject ?: return null
        val recipient = (body["toAddress"] as? JsonPrimitive)?.content ?: return null
        val coin = (body["amount"] as? JsonArray)?.singleOrNull() as? JsonObject ?: return null
        val denom = (coin["denom"] as? JsonPrimitive)?.content ?: return null
        if (!denom.equals(expectedDenom, ignoreCase = true)) return null
        val amount = (coin["amount"] as? JsonPrimitive)?.content?.toBigIntegerOrNull() ?: return null
        return SignDirectTransfer(recipient = recipient, amount = amount)
    }

    private data class SignDirectTransfer(val recipient: String, val amount: BigInteger)

    private companion object {
        const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"
    }
}
