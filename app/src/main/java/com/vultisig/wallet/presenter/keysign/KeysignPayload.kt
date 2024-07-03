package com.vultisig.wallet.presenter.keysign

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.chains.AtomHelper
import com.vultisig.wallet.chains.DydxHelper
import com.vultisig.wallet.chains.ERC20Helper
import com.vultisig.wallet.chains.EvmHelper
import com.vultisig.wallet.chains.KujiraHelper
import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.PolkadotHelper
import com.vultisig.wallet.chains.SolanaHelper
import com.vultisig.wallet.chains.THORCHainHelper
import com.vultisig.wallet.chains.THORChainSwaps
import com.vultisig.wallet.chains.UtxoInfo
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.common.toJson
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.SwapPayload
import com.vultisig.wallet.data.wallet.OneInchSwap
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.models.Vault
import java.lang.reflect.Type
import java.math.BigInteger


internal data class KeysignPayload(
    @SerializedName("coin")
    val coin: Coin,
    @SerializedName("toAddress")
    val toAddress: String,
    @SerializedName("toAmount")
    val toAmount: BigInteger,
    @SerializedName("chainSpecific") val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
    @SerializedName("memo")
    val memo: String? = null,
    val swapPayload: SwapPayload? = null,
    @SerializedName("approvePayload")
    val approvePayload: ERC20ApprovePayload? = null,
    @SerializedName("vaultPubKeyECDSA")
    val vaultPublicKeyECDSA: String,
    @SerializedName("vaultLocalPartyID")
    val vaultLocalPartyID: String,
) {
    fun getKeysignMessages(vault: Vault): List<String> {
        val messages = mutableListOf<String>()

        var nonceAcc = BigInteger.ZERO

        if (approvePayload != null) {
            messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getPreSignedApproveImageHash(approvePayload, this)
            nonceAcc++
        }

        if (swapPayload != null) {
            messages += when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, this, nonceAcc)
                }

                is SwapPayload.OneInch -> {
                    OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, this, nonceAcc)
                }
            }
        } else {
            messages += when (coin.chain) {
                Chain.thorChain -> {
                    val thorHelper = THORCHainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    thorHelper.getPreSignedImageHash(this)
                }

                Chain.solana -> {
                    val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                    solanaHelper.getPreSignedImageHash(this)
                }

                Chain.ethereum, Chain.avalanche, Chain.base, Chain.blast, Chain.arbitrum, Chain.polygon, Chain.optimism, Chain.bscChain, Chain.cronosChain -> {
                    if (coin.isNativeToken) {
                        EvmHelper(
                            coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(this)
                    } else {
                        ERC20Helper(
                            coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(this)
                    }
                }

                Chain.bitcoin, Chain.bitcoinCash, Chain.litecoin, Chain.dogecoin, Chain.dash -> {
                    val utxo =
                        utxoHelper(this.coin.coinType, vault.pubKeyECDSA, vault.hexChainCode)
                    utxo.getPreSignedImageHash(this)
                }

                Chain.gaiaChain -> {
                    val atomHelper = AtomHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    atomHelper.getPreSignedImageHash(this)
                }

                Chain.dydx -> {
                    val dydxHelper = DydxHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    dydxHelper.getPreSignedImageHash(this)
                }

                Chain.kujira -> {
                    val kujiraHelper = KujiraHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    kujiraHelper.getPreSignedImageHash(this)
                }

                Chain.mayaChain -> {
                    val mayachainHelper = MayaChainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                    mayachainHelper.getPreSignedImageHash(this)
                }

                Chain.polkadot -> {
                    val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                    dotHelper.getPreSignedImageHash(this)
                }
            }
        }

        return messages.sorted()
    }
}

internal class KeysignPayloadSerializer : JsonSerializer<KeysignPayload> {
    override fun serialize(
        src: KeysignPayload,
        typeOfSrc: Type?,
        context: JsonSerializationContext?,
    ): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("toAddress", src.toAddress)
        jsonObject.add("toAmount", src.toAmount.toJson())
        jsonObject.addProperty("vaultPubKeyECDSA", src.vaultPublicKeyECDSA)
        jsonObject.add("chainSpecific", context?.serialize(src.blockChainSpecific))
        jsonObject.add("coin", context?.serialize(src.coin))
        jsonObject.add("utxos", context?.serialize(src.utxos))
        jsonObject.addProperty("memo", src.memo ?: "")
        jsonObject.addProperty("vaultLocalPartyID", src.vaultLocalPartyID)
        val swapPayload = src.swapPayload
        if (swapPayload != null) {
            jsonObject.add("swapPayload", context?.serialize(swapPayload))
            val spObject = JsonObject()
            val wrapperObject = JsonObject()
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    wrapperObject.add("_0", context?.serialize(swapPayload.data))
                    spObject.add(
                        "thorchain",
                        wrapperObject
                    )
                }

                is SwapPayload.OneInch -> {
                    wrapperObject.add("_0", context?.serialize(swapPayload.data))
                    spObject.add(
                        "oneInch",
                        wrapperObject
                    )
                }
            }

            jsonObject.add("swapPayload", spObject)
        }
        if (src.approvePayload != null) {
            jsonObject.add("approvePayload", context?.serialize(src.approvePayload))
        }
        return jsonObject
    }
}

internal class KeysignPayloadDeserializer : JsonDeserializer<KeysignPayload> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): KeysignPayload {
        val jsonObject = json.asJsonObject
        val toAddress = jsonObject.get("toAddress").asString
        val toAmount = jsonObject.get("toAmount").asJsonArray[1].asBigInteger
        val vaultPubKeyECDSA = jsonObject.get("vaultPubKeyECDSA").asString
        val vaultLocalPartyID = jsonObject.get("vaultLocalPartyID").asString
        val chainSpecific = context?.deserialize<BlockChainSpecific>(
            jsonObject.get("chainSpecific"), BlockChainSpecific::class.java
        )!!
        val coin = context.deserialize<Coin>(jsonObject.get("coin"), Coin::class.java)
        val utxosType = object : TypeToken<List<UtxoInfo>>() {}.type
        val utxos = context.deserialize<List<UtxoInfo>>(
            jsonObject.get("utxos"), utxosType
        )
        val memo = jsonObject.get("memo")?.asString
        val swapPayloadJsonObject = jsonObject.get("swapPayload")
        val swapPayload: SwapPayload? = if (swapPayloadJsonObject != null) {
            val spo = swapPayloadJsonObject.asJsonObject
            when {
                spo.has("thorchain") -> {
                    SwapPayload.ThorChain(
                        context.deserialize(
                            spo.get("thorchain")
                                .asJsonObject.get("_0"),
                            THORChainSwapPayload::class.java
                        )
                    )
                }

                spo.has("oneInch") -> {
                    SwapPayload.OneInch(
                        context.deserialize(
                            spo.get("oneInch")
                                .asJsonObject.get("_0"),
                            OneInchSwapPayloadJson::class.java
                        )
                    )
                }

                else -> error("KeysignPayload doesn't have a known swapPayload")
            }
        } else null

        var approvePayload: ERC20ApprovePayload? = null
        val approvePayloadJsonObject = jsonObject.get("approvePayload")
        if (approvePayloadJsonObject != null) {
            approvePayload = context.deserialize<ERC20ApprovePayload>(
                jsonObject.get("approvePayload"), ERC20ApprovePayload::class.java
            )
        }
        return KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific = chainSpecific,
            utxos = utxos,
            memo = memo,
            swapPayload = swapPayload,
            approvePayload = approvePayload,
            vaultPublicKeyECDSA = vaultPubKeyECDSA,
            vaultLocalPartyID = vaultLocalPartyID
        )
    }
}