@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import java.math.BigInteger
import timber.log.Timber
import tss.KeysignResponse
import vultisig.keysign.v1.SuiCoin
import wallet.core.jni.AnyAddress
import wallet.core.jni.Base64
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Sui

object SuiHelper {

    private val coinType = CoinType.SUI
    private val suiContractAddress = "0x2::sui::SUI"

    /**
     * Sui accepts a coin type's address part in short (`0x2`) or zero-padded long (`0x000…002`)
     * form, and RPC nodes are not consistent about which they return. Comparing the raw strings
     * would classify the same coin differently on two devices — a native object read as a token
     * object silently turns a `Pay` into a `PaySui`. Only the address is normalized (leading zeros
     * stripped, lowercased); Move module and struct identifiers stay case-sensitive because
     * `::coin::USDC` and `::coin::usdc` are genuinely different types. Mirrors iOS
     * `SuiCoinType.normalize` and SDK `normalizeSuiCoinType` (vultisig-sdk#1275).
     */
    internal fun normalizeSuiCoinType(coinType: String): String {
        val addressEnd = coinType.indexOf("::")
        if (addressEnd < 0) return normalizeSuiAddress(coinType)
        return normalizeSuiAddress(coinType.substring(0, addressEnd)) +
            coinType.substring(addressEnd)
    }

    internal fun isSameSuiCoinType(lhs: String, rhs: String): Boolean =
        normalizeSuiCoinType(lhs) == normalizeSuiCoinType(rhs)

    private fun normalizeSuiAddress(address: String): String {
        val hex = address.lowercase().removePrefix("0x").trimStart('0')
        return "0x" + hex.ifEmpty { "0" }
    }

    private fun SuiCoin.isNativeSui(): Boolean = isSameSuiCoinType(coinType, suiContractAddress)

    /**
     * Upper bound on the coin objects a single send may reference. Sui rejects a transaction whose
     * serialized size exceeds 128 KiB, and a `PaySui` send uses its entire input set as the gas
     * payment — which Sui caps at 256 objects. Staying one under that cap keeps every send safely
     * within both limits. Mirrors iOS `SuiConstants.maxInputCoinObjects`.
     */
    internal const val MAX_INPUT_COIN_OBJECTS = 255

    /**
     * How many of the largest native SUI objects to embed as gas candidates for a token send. The
     * signer picks one to pay gas; carrying the largest few (rather than all, or just one) keeps
     * the payload small while guaranteeing a covering object survives a re-estimated gas budget.
     * Mirrors iOS `SuiConstants.gasCandidateObjectCount`.
     */
    internal const val GAS_CANDIDATE_OBJECT_COUNT = 5

    internal fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(keysignPayload.coin.chain == Chain.Sui) { "Coin is not SUI" }

        // dApp-supplied Programmable Transaction Block (Sui Wallet Standard): the base64
        // `TransactionData` BCS bytes are already complete (coins, gas budget, recipients baked
        // in), so we sign them verbatim via WalletCore's SignDirect path rather than rebuilding a
        // Pay / PaySui. WalletCore hashes the bytes under the Sui transaction intent.
        keysignPayload.signSui?.let { signSui ->
            return buildSignDirectInputData(
                signer = keysignPayload.coin.address,
                unsignedTxMsg = signSui.unsignedTxMsg,
            )
        }

        val (referenceGasPrice, gasBudget, coins) =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Sui
                ?: throw RuntimeException(
                    "getPreSignedInputData fail to get SUI transaction information from RPC"
                )

        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val suiObjects = coins.filter { it.isNativeSui() }
        val tokenObjects =
            coins.filter {
                isSameSuiCoinType(it.coinType, keysignPayload.coin.contractAddress) &&
                    !it.isNativeSui()
            }

        val input =
            if (tokenObjects.isNotEmpty()) {
                    // Token send (Pay): reference only the largest token objects covering the
                    // amount, and pay gas from a single native SUI object that covers the budget.
                    val gasCoin = selectSuiGasCoin(coins, gasBudget)
                    val gasObjectRef =
                        selectSuiGasObjectRef(
                            suiObjects.map { it.toObjectRef() },
                            gasCoin?.coinObjectId,
                        )
                    val tokenInputCoins =
                        selectInputCoins(tokenObjects, keysignPayload.toAmount).map {
                            it.toObjectRef()
                        }
                    Sui.SigningInput.newBuilder()
                        .setPay(
                            Sui.Pay.newBuilder()
                                .setGas(gasObjectRef)
                                .addAllInputCoins(tokenInputCoins)
                                .addAllRecipients(listOf(toAddress.description()))
                                .addAllAmounts(listOf(keysignPayload.toAmount.toLong()))
                                .build()
                        )
                } else {
                    // Native send (PaySui): the input set also pays gas (Sui gas-smashes it into
                    // one coin), so cover amount + gas with the fewest largest objects.
                    val target = keysignPayload.toAmount + gasBudget
                    val nativeInputCoins =
                        selectInputCoins(suiObjects, target).map { it.toObjectRef() }
                    Sui.SigningInput.newBuilder()
                        .setPaySui(
                            Sui.PaySui.newBuilder()
                                .addAllInputCoins(nativeInputCoins)
                                .addAllRecipients(listOf(toAddress.description()))
                                .addAllAmounts(listOf(keysignPayload.toAmount.toLong()))
                                .build()
                        )
                }
                .setSigner(keysignPayload.coin.address)
                .setGasBudget(gasBudget.toLong())
                .setReferenceGasPrice(referenceGasPrice.toLong())
                .build()

        return input.toByteArray()
    }

    private fun getPreSigningOutput(
        keysignPayload: KeysignPayload
    ): wallet.core.jni.proto.TransactionCompiler.PreSigningOutput {
        val inputData = getPreSignedInputData(keysignPayload)
        Timber.d("input data: ${inputData.toHexString()}")

        val hashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        require(preSigningOutput.errorMessage.isEmpty()) { preSigningOutput.errorMessage }

        return preSigningOutput
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val preSigningOutput = getPreSigningOutput(keysignPayload)
        return listOf(preSigningOutput.dataHash.toByteArray().toHexString())
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val preSigningOutput = getPreSigningOutput(keysignPayload)

        // Drop 3 first bytes which represents signature, they're added by WalletCore
        // but for simulations or blockaid is not required
        val tx = preSigningOutput.data.toByteArray().drop(3).toByteArray()

        return Base64.encode(tx)
    }

    /**
     * Builds the WalletCore `Sui.SigningInput` bytes for a dApp-supplied PTB ([SignSui]). The
     * `SignDirect` payload carries the base64 `TransactionData` BCS bytes verbatim — no `Pay` /
     * `PaySui`, gas budget, or reference gas price, since those are already encoded in the bytes.
     * Pure protobuf assembly (no JNI), so it is unit-testable.
     */
    internal fun buildSignDirectInputData(signer: String, unsignedTxMsg: String): ByteArray {
        require(unsignedTxMsg.isNotEmpty()) { "SignSui unsignedTxMsg is empty" }
        return Sui.SigningInput.newBuilder()
            .setSignDirectMessage(Sui.SignDirect.newBuilder().setUnsignedTxMsg(unsignedTxMsg))
            .setSigner(signer)
            .build()
            .toByteArray()
    }

    /**
     * The smallest native SUI object that covers [gasBudget], `coinObjectId` ascending as
     * tie-break. The tie-break is a consensus requirement, not a preference: picking the first
     * minimum in list order instead would let a device that sorts the same shared coin list
     * differently choose a different gas object among equal-balance candidates, producing different
     * transaction bytes. Mirrors iOS `SuiCoinType.selectGasObject` and SDK `selectSuiGasObject`.
     */
    internal fun selectSuiGasCoin(coins: List<SuiCoin>, gasBudget: BigInteger): SuiCoin? =
        coins
            .filter { it.isNativeSui() && it.balanceOrZero() >= gasBudget }
            .minWithOrNull(compareBy<SuiCoin> { it.balanceOrZero() }.thenBy { it.coinObjectId })

    /**
     * The fewest coin objects (largest balance first, `coinObjectId` ascending as tie-break) whose
     * balances together cover [target], bounded by [maxObjects]. Selection is deterministic so
     * every co-signing device selects the identical set and builds byte-identical transaction bytes
     * — a cross-device keysign consensus requirement, not merely an optimization.
     *
     * Referencing every owned object is what trips Sui's 128 KiB transaction-size limit and, for a
     * native `PaySui` send, the 256-object gas-payment cap ("serialized transaction size exceeded
     * maximum") on wallets whose balance is scattered across many objects. Taking only the largest
     * objects the send needs keeps the transaction small while still merging a scattered balance
     * (Sui gas-smashes a native input set into one spendable coin).
     *
     * At least one object is always returned. If even [maxObjects] largest objects do not reach
     * [target] they are still returned (best effort) — the caller decides how to handle an
     * under-funded selection. Mirrors iOS `SuiCoinType.selectInputCoins` (vultisig-ios#4734).
     */
    internal fun selectInputCoins(
        coins: List<SuiCoin>,
        target: BigInteger,
        maxObjects: Int = MAX_INPUT_COIN_OBJECTS,
    ): List<SuiCoin> {
        val sorted =
            coins.sortedWith(
                compareByDescending<SuiCoin> { it.balanceOrZero() }.thenBy { it.coinObjectId }
            )

        val selected = mutableListOf<SuiCoin>()
        var accumulated = BigInteger.ZERO
        for (coin in sorted) {
            // Keep at least one object so a zero/near-zero-amount send still has an input;
            // otherwise stop once the target is covered.
            if (selected.isNotEmpty() && accumulated >= target) break
            if (selected.size >= maxObjects) break
            selected.add(coin)
            accumulated += coin.balanceOrZero()
        }
        return selected
    }

    /**
     * The minimal set of coin objects to embed in the keysign payload for a Sui send — exactly what
     * [getPreSignedInputData] will consume. Bounding the embedded set keeps the pairing QR / TSS
     * relay message small: on a wallet whose balance is spread across thousands of objects,
     * embedding every one produces a payload too large to relay (the co-signer's poll 404s and the
     * initiator's transaction data expires before signing can start).
     *
     * Native send: the largest native objects covering `amount + gasBudget` (the input set also
     * pays gas). Token send: the largest token objects covering `amount`, plus the largest few
     * native SUI objects as gas candidates (one is selected to pay gas at signing time). Mirrors
     * iOS `SuiCoinType.selectPayloadCoins` (vultisig-ios#4734).
     */
    internal fun selectPayloadCoins(
        coins: List<SuiCoin>,
        isNativeToken: Boolean,
        contractAddress: String,
        amount: BigInteger,
        gasBudget: BigInteger,
    ): List<SuiCoin> {
        val nativeObjects = coins.filter { it.isNativeSui() }

        if (isNativeToken) {
            return selectInputCoins(nativeObjects, amount + gasBudget)
        }

        val tokenObjects =
            coins.filter { isSameSuiCoinType(it.coinType, contractAddress) && !it.isNativeSui() }
        val selectedTokens = selectInputCoins(tokenObjects, amount)
        val gasCandidates =
            nativeObjects
                .sortedWith(
                    compareByDescending<SuiCoin> { it.balanceOrZero() }.thenBy { it.coinObjectId }
                )
                .take(GAS_CANDIDATE_OBJECT_COUNT)
        return selectedTokens + gasCandidates
    }

    private fun SuiCoin.balanceOrZero(): BigInteger =
        balance.toBigIntegerOrNull() ?: BigInteger.ZERO

    private fun SuiCoin.toObjectRef(): Sui.ObjectRef =
        Sui.ObjectRef.newBuilder()
            .setObjectId(coinObjectId)
            .setVersion(version.toLong())
            .setObjectDigest(digest)
            .build()

    internal fun selectSuiGasObjectRef(
        suiObjectRefs: List<Sui.ObjectRef>,
        gasCoinObjectId: String?,
    ): Sui.ObjectRef =
        suiObjectRefs.firstOrNull { it.objectId == gasCoinObjectId }
            ?: error("No suitable SUI gas coin available for transaction")

    fun getSignedTransaction(
        vaultHexPubKey: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val pubkeyData = vaultHexPubKey.hexToByteArray()
        val publicKey = PublicKey(pubkeyData, PublicKeyType.ED25519)

        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val preSigningOutputDataBlake2b = preSigningOutput.dataHash.toByteArray()

        val key = preSigningOutputDataBlake2b.toHexString()

        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val signature = signatures[key]?.getSignature() ?: throw Exception("Signature not found")

        if (!publicKey.verify(signature, preSigningOutputDataBlake2b)) {
            throw Exception("Signature verification failed")
        }

        allSignatures.add(signature)
        publicKeys.add(pubkeyData)
        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                inputData,
                allSignatures,
                publicKeys,
            )
        val output = Sui.SigningOutput.parseFrom(compileWithSignature).checkError()
        return SignedTransactionResult(output.unsignedTx, "", output.signature)
    }
}

internal val DEFAULT_SUI_GAS_BUDGET = "3000000".toBigInteger()
