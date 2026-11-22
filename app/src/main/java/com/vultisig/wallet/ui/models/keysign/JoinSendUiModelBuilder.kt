@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.RippleDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.SubstrateDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.common.asCallData
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.cosmosNativeDenom
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.DeFiAction
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
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.transaction.CosmosAmountUiModel
import com.vultisig.wallet.ui.models.transaction.CosmosTransfersUiModel
import com.vultisig.wallet.ui.models.transaction.VerifyTransactionUiModel
import com.vultisig.wallet.ui.utils.resolveDstVaultName
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import vultisig.keysign.v1.SignAmino
import wallet.core.jni.TONAddressConverter
import wallet.core.jni.proto.Common.SigningError

/**
 * Builds a co-signer's send review and canonical scanner input. The original signing payload
 * stays unchanged. Wallet metadata comes from the receiving device or the chain.
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
        val chain = payloadToken.chain
        val vault = withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: return null
        val (address, publicKey) =
            withContext(Dispatchers.IO) { chainAccountAddressRepository.getAddress(chain, vault) }
        val nativeCoin =
            withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
                .copy(address = address, hexPublicKey = publicKey)
        val signsRawSolana = chain == Chain.Solana && payload.signSolana != null
        val signsRawSui = chain == Chain.Sui && payload.signSui != null
        val signsRawTransaction = signsRawSolana || signsRawSui
        val isCircleWithdrawal =
            chain.standard == TokenStandard.EVM &&
                !payloadToken.isNativeToken &&
                payload.defiAction == DeFiAction.CIRCLE_USDC_WITHDRAW
        val resolvedToken =
            if (payloadToken.isNativeToken || signsRawTransaction || isCircleWithdrawal) nativeCoin
            else resolveReviewToken(payloadToken, vault.coins)
        val displayToken =
            (resolvedToken
                    ?: Coin.EMPTY.copy(chain = chain, contractAddress = payloadToken.contractAddress))
                .copy(address = address, hexPublicKey = publicKey)

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
                        decimals = displayToken.decimal,
                        ticker = displayToken.ticker,
                    )
                }
                    ?.transfer

        // A Cosmos `signDirect` / `signAmino` request is signed as its SignDoc bytes or amino
        // `msgs`, so the recipient and amount must be read out of that signed content — never from
        // the wire `toAddress` / `toAmount`, which are the initiator's unverified display copy. A
        // compromised initiator could otherwise show the co-signer one recipient while the signed
        // messages pay another. When the request isn't a single bank send (staking, multi-message,
        // dApp calls) no simple To/amount is shown; the signed content is rendered below in its
        // Direct Sign / amino card.
        val signDirectMessage = payload.signDirect?.let { parseCosmosMessage(it) }
        val isCosmosSignDoc = payload.signDirect != null || payload.signAmino != null
        // CosmosHelper signs `signAmino` ahead of `signDirect` when a payload carries both, so the
        // review reads the same one.
        val signAmino = payload.signAmino
        // The signed coin's denom has to match what the row will be labelled with, or the signed
        // integer would render under the wrong ticker/decimals. Native sends carry the chain's
        // native denom; other Cosmos assets carry their denom in the coin's contract address.
        val expectedCosmosDenom =
            if (payloadToken.isNativeToken) chain.cosmosNativeDenom
            else payloadToken.contractAddress
        val cosmosSignedTransfer =
            if (signAmino != null) signAmino.singleAminoSendTransfer(expectedCosmosDenom)
            else signDirectMessage?.singleMsgSendTransfer(expectedCosmosDenom)

        val dstAddress =
            when {
                signsRawTransaction -> ""
                substrateDapp != null -> substrateTransfer?.recipient.orEmpty()
                isCosmosSignDoc -> cosmosSignedTransfer?.recipient.orEmpty()
                else -> payload.toAddress
            }
        val amount =
            when {
                signsRawTransaction || isCircleWithdrawal -> BigInteger.ZERO
                substrateDapp != null -> substrateTransfer?.amount ?: BigInteger.ZERO
                isCosmosSignDoc -> cosmosSignedTransfer?.amount ?: BigInteger.ZERO
                else -> payload.toAmount
            }

        val tokenValue = TokenValue(value = amount, token = displayToken)
        val signedMemo =
            when {
                signsRawTransaction -> null
                chain.standard != TokenStandard.EVM -> payload.memo
                payloadToken.isNativeToken || isCircleWithdrawal ->
                    payload.memo?.toByteStringOrHex()?.toByteArray()?.asCallData()
                else -> null // The ordinary ERC-20 signer encodes a transfer, without a memo.
            }

        val blockchainTransaction =
            Transfer(
                coin = displayToken,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = tokenValue.value,
                to = dstAddress,
                memo = signedMemo,
                isMax = false,
            )

        // A dApp XRPL tx is signed verbatim, so the fee that is actually paid is the `Fee` baked
        // into its raw JSON — not a live re-estimate. Surface that exact value so an inflated Fee
        // is
        // visible on the co-signer's Verify screen instead of being masked by a normal-looking
        // RippleFeeService estimate.
        val rippleDappFeeDrops =
            payload.signRipple?.rawJson?.let { RippleDappTransactionDecoder.feeDrops(it) }
        val gasFee =
            when {
                signsRawTransaction -> TokenValue(value = BigInteger.ZERO, token = nativeCoin)

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
                    selectedToken = displayToken,
                )
            )
        // A Tron dApp contract call is signed from its `data`, not from the memo, so that is what
        // gets decoded; otherwise an `approve` would render as a plain 0 TRX send to the contract.
        val calldata =
            if (chain == Chain.Tron) payload.tronTriggerSmartContractPayload?.data
            else signedMemo
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

        val signSolana =
            if (signsRawSolana) payload.signSolana?.rawTransactions.orEmpty() else emptyList()
        val signSui = payload.signSui?.unsignedTxMsg?.takeIf { signsRawSui && it.isNotEmpty() }
        val signRipple = payload.signRipple?.rawJson?.takeIf { it.isNotBlank() }
        val transaction =
            Transaction(
                id = Uuid.random().toString(),
                vaultId = payload.vaultPublicKeyECDSA,
                chainId = chain.id,
                token = displayToken,
                srcAddress = address,
                dstAddress = dstAddress,
                tokenValue = tokenValue,
                fiatValue =
                    if (resolvedToken == null || signsRawTransaction)
                        FiatValue(BigDecimal.ZERO, currency.ticker)
                    else convertTokenValueToFiat(displayToken, tokenValue, currency),
                gasFee = gasFee,
                memo = signedMemo,
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

        // Render decoded calldata in its details card. Keep the original bytes in the scanner
        // input so a contract call can never acquire the verdict of a plain transfer.
        val presentationMemo =
            signedMemo.takeIf {
                (functionInfo == null || chain == Chain.Tron) && substrateDapp == null
            }
        val transactionToUiModel = mapTransactionToUiModel(transaction.copy(memo = presentationMemo))

        val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
        val dstVaultName =
            if (dstAddress.isEmpty()) {
                null
            } else {
                resolveDstVaultName(
                    allVaults = allVaults,
                    chain = chain,
                    dstAddress = dstAddress,
                    chainAccountAddressRepository = chainAccountAddressRepository,
                )
            }
        val dstAddressBookTitle =
            if (dstVaultName == null && dstAddress.isNotEmpty()) {
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
            mapTonMessages(payload.signTon, fromAddress = address) { rawAddress ->
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
                isRawCoSign = signsRawTransaction,
                unverifiedAssetAmount =
                    if (resolvedToken == null && (!isCosmosSignDoc || cosmosSignedTransfer != null))
                        "$amount ${payloadToken.contractAddress}".trimEnd()
                    else null,
                heroContent =
                    HeroContent.Unverified.takeIf { signsRawTransaction || resolvedToken == null },
                isUnverifiedCosmosSign = isCosmosSignDoc && cosmosSignedTransfer == null,
                cosmosTransfers =
                    if (isCosmosSignDoc && cosmosSignedTransfer == null) {
                        val review =
                            if (signAmino != null) signAmino.toSignDataReview(chain, json)
                            else signDirectMessage?.toSignDataReview(chain)
                        review?.toUiModel(chain, nativeCoin)
                    } else null,
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
            simulationPayload =
                payload.copy(
                    coin = displayToken,
                    toAddress = dstAddress,
                    toAmount = amount,
                    memo = signedMemo,
                ),
            functionName = functionInfo?.functionName,
            vaultCoins = vault.coins,
        )
    }

    private suspend fun resolveReviewToken(payload: Coin, vaultCoins: List<Coin>): Coin? {
        val contract = payload.contractAddress
        if (contract.isBlank()) return null
        val ignoreCase = payload.chain.standard == TokenStandard.EVM
        fun matches(coin: Coin) =
            coin.chain == payload.chain &&
                !coin.isNativeToken &&
                coin.contractAddress.equals(contract, ignoreCase = ignoreCase) &&
                coin.decimal in 0..TokenMetadataResolver.MAX_DECIMALS

        val known =
            Coins.findCuratedByContract(payload.chain, contract)?.takeIf(::matches)
                ?: vaultCoins.firstOrNull(::matches)
        if (known != null) return known.copy(contractAddress = contract)
        val metadata = tokenMetadataResolver.resolve(payload.chain, contract) ?: return null
        return Coin.EMPTY.copy(
            chain = payload.chain,
            contractAddress = contract,
            ticker = metadata.symbol,
            decimal = metadata.decimals,
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

/**
 * Pulls the recipient and amount out of a decoded `signDirect` SignDoc, but only when the body is
 * exactly one bank `MsgSend` carrying a single coin whose denom is [expectedDenom]. That is the one
 * case where a simple "To / amount" row can faithfully stand in for the signed bytes. Anything else
 * — staking, governance, multi-message or dApp bodies, or a coin in a denom that doesn't match what
 * the row would be labelled with — returns null so the verify screen shows no spoofable To/amount
 * and relies on the full Direct Sign card instead. The recipient and amount are taken verbatim from
 * the signed bytes, so what the co-signer sees is what it signs.
 */
internal fun CosmosMessage.singleMsgSendTransfer(expectedDenom: String?): CosmosSignedTransfer? {
    if (expectedDenom.isNullOrEmpty()) return null
    val message = messages.singleOrNull() ?: return null
    if (message.typeUrl != MSG_SEND_TYPE_URL) return null
    val body = message.value as? JsonObject ?: return null
    val recipient = (body["toAddress"] as? JsonPrimitive)?.content ?: return null
    val coin = (body["amount"] as? JsonArray)?.singleOrNull() as? JsonObject ?: return null
    val denom = (coin["denom"] as? JsonPrimitive)?.content ?: return null
    if (denom != expectedDenom) return null
    val amount = (coin["amount"] as? JsonPrimitive)?.content?.toBigIntegerOrNull() ?: return null
    return CosmosSignedTransfer(recipient = recipient, amount = amount)
}

/**
 * Pulls the recipient and amount out of a `signAmino` request's signed `msgs`, under the same rule
 * as [singleMsgSendTransfer]: exactly one bank send (`cosmos-sdk/MsgSend`, `thorchain/MsgSend` or
 * `bank/MsgSend`) carrying a single coin whose denom is [expectedDenom]. Anything else — other
 * message types, several messages, several coins, a mismatched denom, or a `value` that isn't
 * readable JSON — returns null, so no recipient or amount is shown from the unsigned wire fields.
 */
internal fun SignAmino.singleAminoSendTransfer(expectedDenom: String?): CosmosSignedTransfer? {
    if (expectedDenom.isNullOrEmpty()) return null
    val message = msgs.singleOrNull() ?: return null
    if (message.type !in AMINO_SEND_TYPES) return null
    val body =
        try {
            aminoSendJson.decodeFromString<AminoSendValue>(message.value)
        } catch (_: SerializationException) {
            return null
        }
    val recipient = body.toAddress.takeIf { it.isNotEmpty() } ?: return null
    val coin = body.amount.singleOrNull()?.takeIf { it.denom == expectedDenom } ?: return null
    val amount =
        coin.amount.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toBigInteger()
            ?: return null
    return CosmosSignedTransfer(recipient = recipient, amount = amount)
}

@Serializable
private data class AminoSendValue(
    @SerialName("to_address") val toAddress: String,
    val amount: List<AminoCoin>,
)

@Serializable private data class AminoCoin(val denom: String, val amount: String)

private val aminoSendJson = Json { ignoreUnknownKeys = true }

/**
 * Formats a review for the verify screen. A coin is labelled with its ticker and decimals only when
 * its denom is the chain's native denom or a known token's; otherwise it shows the raw amount and
 * denom and is flagged unverified, so an unlisted denom named like a known coin can't pass for it.
 * Every dApp-supplied string is stripped of control, bidi and zero-width characters, so a newline
 * or reordering mark can't fake an extra line in the joined rows.
 */
internal fun CosmosSignDataReview.toUiModel(
    chain: Chain,
    nativeCoin: Coin,
): CosmosTransfersUiModel =
    CosmosTransfersUiModel(
        recipients = recipients.map(::sanitizeDisplayString).distinct(),
        amounts = coinTotals.map { it.format(chain, nativeCoin) },
        memos = memos.map(::sanitizeDisplayString).distinct(),
        isPartial = hasUnsummarizedMessages,
    )

private fun CosmosSignedCoin.format(chain: Chain, nativeCoin: Coin): CosmosAmountUiModel {
    val metadata =
        if (denom == chain.cosmosNativeDenom) nativeCoin
        else Coins.coins[chain]?.firstOrNull { !it.isNativeToken && it.contractAddress == denom }
    if (metadata == null) {
        return CosmosAmountUiModel(
            text = "$amount ${sanitizeDisplayString(denom)}",
            isKnownDenom = false,
        )
    }
    val value = BigDecimal(amount, metadata.decimal).stripTrailingZeros().toPlainString()
    return CosmosAmountUiModel(text = "$value ${metadata.ticker}", isKnownDenom = true)
}

/** A recipient and amount read out of a Cosmos request's signed content. */
internal data class CosmosSignedTransfer(val recipient: String, val amount: BigInteger)

private const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"

private val AMINO_SEND_TYPES = setOf("cosmos-sdk/MsgSend", "thorchain/MsgSend", "bank/MsgSend")
