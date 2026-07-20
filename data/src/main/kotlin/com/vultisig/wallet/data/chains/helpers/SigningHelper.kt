@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.swapAggregators.OneInchSwap
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.common.toSha256ByteArray
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getEcdsaSigningKey
import com.vultisig.wallet.data.models.getEddsaSigningKey
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.payload.carriesDappCosmosTx
import com.vultisig.wallet.data.models.payload.zcashBranchId
import java.math.BigInteger
import vultisig.keysign.v1.CustomMessagePayload
import wallet.core.jni.EthereumAbi

object SigningHelper {
    private const val ETH_SIGN_TYPED_DATA_V4 = "eth_signTypedData_v4"

    @OptIn(ExperimentalStdlibApi::class)
    fun getKeysignMessages(messagePayload: CustomMessagePayload): List<String> =
        getKeysignMessages(messagePayload, typedDataHasher = EthereumAbi::encodeTyped)

    @OptIn(ExperimentalStdlibApi::class)
    internal fun getKeysignMessages(
        messagePayload: CustomMessagePayload,
        typedDataHasher: (String) -> ByteArray?,
    ): List<String> {
        if (messagePayload.method.equals(ETH_SIGN_TYPED_DATA_V4, ignoreCase = true)) {
            return getKeysignMessagesForTypedData(messagePayload.message, typedDataHasher)
        }

        // Only a `0x`-prefixed message is treated as hex; everything else is UTF-8. iOS
        // (CustomMessagePayload.keysignMessages) and Windows (getCustomMessageHex.ts) both gate
        // hex decoding on the `0x` prefix, so a plain-text message made only of hex characters
        // (e.g. "Vultisig") must be hashed as UTF-8 — otherwise the digest, and the md5 message-ID
        // derived from it, diverges and cross-platform co-signing can't locate the setup message.
        val processedBytes =
            if (messagePayload.message.startsWith("0x")) {
                messagePayload.message.toHexBytes()
            } else {
                messagePayload.message.toByteArray()
            }
        val chain =
            messagePayload.chain?.let { raw -> runCatching { Chain.fromRaw(raw) }.getOrNull() }
        // Match the message digest to the chain, mirroring iOS
        // (CustomMessagePayload.keysignMessages) and Windows (getCustomMessageHex.ts):
        //  - Cosmos-family chains (incl. THORChain/Maya) sign the sha256 of the message
        //    (Keplr ADR-36 signArbitrary over the StdSignDoc bytes). Previously these were
        //    keccak256'd, so the digest — and the md5 message-ID derived from it — diverged
        //    from the iOS/Windows/CLI initiator and cross-platform co-signing 404'd.
        //  - EdDSA chains (e.g. Solana, TON) deliver the precomputed digest — sign it raw.
        //  - Everything else (EVM, Tron) signs the keccak256.
        val bytes =
            when {
                chain?.standard == TokenStandard.COSMOS ||
                    chain?.standard == TokenStandard.THORCHAIN -> processedBytes.toSha256ByteArray()
                chain?.TssKeysignType == TssKeyType.EDDSA -> processedBytes
                else -> processedBytes.toKeccak256ByteArray()
            }
        return listOf(bytes.toHexString())
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getKeysignMessagesForTypedData(
        message: String,
        hashFn: (String) -> ByteArray?,
    ): List<String> {
        val hash = hashFn(message)
        if (hash == null || hash.isEmpty()) {
            error("Invalid eth_signTypedData_v4 message")
        }
        return listOf(hash.toHexString())
    }

    fun getKeysignMessages(payload: KeysignPayload, vault: Vault): List<String> {
        val messages = mutableListOf<String>()
        val chain = payload.coin.chain
        val (ecdsaKey, ecdsaChainCode) = vault.getEcdsaSigningKey(chain)
        val eddsaKey = vault.getEddsaSigningKey(chain)

        var nonceAcc = BigInteger.ZERO

        val approvePayload = payload.approvePayload
        if (approvePayload != null) {
            messages +=
                THORChainSwaps(ecdsaKey, ecdsaChainCode, eddsaKey)
                    .getPreSignedApproveImageHash(approvePayload, payload)
            nonceAcc++
        }

        val swapPayload = payload.swapPayload
        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    messages +=
                        THORChainSwaps(ecdsaKey, ecdsaChainCode, eddsaKey)
                            .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
                }
                is SwapPayload.EVM -> {
                    val message =
                        if (payload.coin.chain == Chain.Solana) {
                            SolanaSwap(eddsaKey).getPreSignedImageHash(swapPayload.data, payload)
                        } else
                            OneInchSwap(ecdsaKey, ecdsaChainCode)
                                .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)

                    messages += message
                }
                is SwapPayload.SwapKit -> {
                    val txType = swapPayload.data.txType
                    // SwapKit's documented wire value for the whole UTXO family is the generic
                    // "PSBT" — sometimes arriving blank when a peer's SDK omits `meta.txType` for
                    // the non-Bitcoin variants. Android/iOS additionally stamp per-chain
                    // PSBT_DOGE/PSBT_BCH/PSBT_DASH/PSBT_ZEC onto their own initiated swaps (kept
                    // for backward compatibility). Either way the actual signer must come from the
                    // payload's own `chain`, not from which literal happened to match, or a peer
                    // that doesn't share the invented convention can't be joined.
                    messages +=
                        if (SwapKitSwapPayloadJson.isUtxoPsbtTxType(txType)) {
                            when (chain) {
                                // Segwit PSBT (BTC + LTC). CoinType is picked from the source
                                // chain; the BIP-143 sighash + segwit serialization are otherwise
                                // identical.
                                Chain.Bitcoin,
                                Chain.Litecoin ->
                                    SwapKitBtcSigner(ecdsaKey, ecdsaChainCode, chain.coinType)
                                        .getPreSignedImageHash(
                                            psbtBytes = swapPayload.data.txPayload,
                                            targetAddress = swapPayload.data.targetAddress,
                                            fromAmount = swapPayload.data.fromAmount,
                                        )
                                // Legacy P2PKH UTXO chains (DOGE / BCH / DASH). DOGE/DASH use
                                // classic ECDSA sighashing; BCH adds SIGHASH_FORKID via its
                                // CoinType.
                                Chain.BitcoinCash,
                                Chain.Dogecoin,
                                Chain.Dash ->
                                    SwapKitLegacyP2PKHSigner(
                                            ecdsaKey,
                                            ecdsaChainCode,
                                            chain.coinType,
                                        )
                                        .getPreSignedImageHash(
                                            psbtBytes = swapPayload.data.txPayload,
                                            targetAddress = swapPayload.data.targetAddress,
                                            fromAmount = swapPayload.data.fromAmount,
                                        )
                                // Transparent ZEC (Sapling-v4 body, ZIP-243 sighash).
                                Chain.Zcash ->
                                    SwapKitZcashSigner(ecdsaKey, ecdsaChainCode)
                                        .getPreSignedImageHash(
                                            psbtBytes = swapPayload.data.txPayload,
                                            targetAddress = swapPayload.data.targetAddress,
                                            fromAmount = swapPayload.data.fromAmount,
                                            zcashBranchId = payload.zcashBranchId,
                                        )
                                else -> error("Unsupported SwapKit txType for signing: $txType")
                            }
                        } else {
                            when (txType) {
                                SwapKitSwapPayloadJson.TX_TYPE_TRON ->
                                    SwapKitTronSigner(ecdsaKey, ecdsaChainCode)
                                        .getPreSignedImageHash(swapPayload.data.txPayload)
                                SwapKitSwapPayloadJson.TX_TYPE_SUI ->
                                    SwapKitSuiSigner(eddsaKey)
                                        .getPreSignedImageHash(swapPayload.data.txPayload)
                                SwapKitSwapPayloadJson.TX_TYPE_CARDANO_PREBUILT ->
                                    SwapKitCardanoSigner(eddsaKey)
                                        .getPreSignedImageHash(swapPayload.data.txPayload)
                                // Deposit-only Cardano: no CBOR to sign — hash a plain ADA send to
                                // targetAddress built from the blockChainSpecific, via the native
                                // path.
                                SwapKitSwapPayloadJson.TX_TYPE_CARDANO ->
                                    CardanoHelper.getPreSignedImageHash(payload)
                                // TON SwapKit is a plain native transfer to the deposit address.
                                // The KeysignPayload already carries toAddress / toAmount / Ton
                                // specifics. It reuses the native TonHelper path (no signTon,
                                // matching iOS).
                                SwapKitSwapPayloadJson.TX_TYPE_TON ->
                                    TonHelper.getPreSignedImageHash(payload)
                                // XRP is deposit-only: no SwapKit signer. SwapPayloadBuilder
                                // already pointed the KeysignPayload's toAddress / toAmount / memo
                                // at the deposit r-address, amount, and destination tag, so we
                                // reuse the native RippleHelper path (which turns a numeric memo
                                // into the destinationTag). Matches iOS, which lets XRP fall
                                // through to the per-chain helper.
                                SwapKitSwapPayloadJson.TX_TYPE_XRP ->
                                    RippleHelper.getPreSignedImageHash(payload)
                                else -> error("Unsupported SwapKit txType for signing: $txType")
                            }
                        }
                }
                else -> Unit
            }
        } else if (swapPayload is SwapPayload.MayaChain && !swapPayload.srcToken.isNativeToken) {
            messages +=
                THORChainSwaps(ecdsaKey, ecdsaChainCode, eddsaKey)
                    .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
        } else {
            messages +=
                when (chain) {
                    Chain.ThorChain -> {
                        val thorHelper = ThorChainHelper.thor(ecdsaKey, ecdsaChainCode)
                        thorHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Solana -> {
                        val solanaHelper = SolanaHelper(eddsaKey)
                        solanaHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Ethereum,
                    Chain.Avalanche,
                    Chain.Base,
                    Chain.Blast,
                    Chain.Arbitrum,
                    Chain.Mantle,
                    Chain.Polygon,
                    Chain.Optimism,
                    Chain.BscChain,
                    Chain.CronosChain,
                    Chain.ZkSync,
                    Chain.Sei,
                    Chain.Hyperliquid -> {
                        if (payload.coin.isNativeToken) {
                            EvmHelper(payload.coin.coinType, ecdsaKey, ecdsaChainCode)
                                .getPreSignedImageHash(payload)
                        } else {
                            ERC20Helper(payload.coin.coinType, ecdsaKey, ecdsaChainCode)
                                .getPreSignedImageHash(payload)
                        }
                    }

                    Chain.Bitcoin,
                    Chain.BitcoinCash,
                    Chain.Litecoin,
                    Chain.Dogecoin,
                    Chain.Dash,
                    Chain.Zcash -> {
                        val utxo = UtxoHelper(payload.coin.coinType, ecdsaKey, ecdsaChainCode)
                        val sb = payload.signBitcoin
                        // PSBT co-signing helper supports P2WPKH / P2SH-P2WPKH only; the UTXO
                        // siblings (BCH, Doge, LTC, Dash, Zcash) use legacy P2PKH, so a
                        // `signBitcoin` block must never reach the silent fall-through. Fail
                        // loudly here so a future PSBT integration on those chains can't ship
                        // with zero binding-check enforcement.
                        require(chain == Chain.Bitcoin || sb == null) {
                            "PSBT co-signing (signBitcoin) is only supported on Bitcoin; " +
                                "got chain $chain — UTXO-sibling dispatch needs updating"
                        }
                        if (sb != null) {
                            utxo.getPreSignedImageHashFromSignBitcoin(
                                signBitcoin = sb,
                                expectedToAddress = payload.toAddress,
                                expectedToAmount = payload.toAmount,
                            )
                        } else {
                            // UtxoHelper reads the live ZEC branch id straight off the payload's
                            // UTXO specific (no-op for the other UTXO chains).
                            utxo.getPreSignedImageHash(payload)
                        }
                    }

                    Chain.Qbtc -> {
                        QBTCTransactionHelper().getPreSignedImageHash(payload)
                    }

                    Chain.GaiaChain,
                    Chain.Kujira,
                    Chain.Dydx,
                    Chain.Osmosis,
                    Chain.Noble,
                    Chain.Akash -> {
                        CosmosHelper(
                                coinType = chain.coinType,
                                denom = chain.feeUnit,
                                gasLimit = CosmosHelper.getChainGasLimit(chain),
                            )
                            .getPreSignedImageHash(payload)
                    }

                    Chain.Terra,
                    Chain.TerraClassic -> {
                        // dApp staking flows carry a pre-encoded Cosmos tx — a protobuf SignDoc on
                        // `signDirect` (MsgDelegate / MsgUndelegate / MsgBeginRedelegate /
                        // MsgWithdrawDelegatorReward) or legacy amino JSON on `signAmino` (the path
                        // Keplr-style sites like cosmosrescue.com use for LUNC). TerraHelper only
                        // knows bank sends + CW20 transfers and would otherwise sign a MsgSend to
                        // the `terravaloper…` validator — the chain rejects that with "invalid to
                        // address: hrp does not match bech32 prefix". Route either through
                        // CosmosHelper, which signs the supplied tx verbatim.
                        if (payload.carriesDappCosmosTx) {
                            CosmosHelper(
                                    coinType = chain.coinType,
                                    denom = chain.feeUnit,
                                    gasLimit = CosmosHelper.getChainGasLimit(chain),
                                )
                                .getPreSignedImageHash(payload)
                        } else {
                            TerraHelper(
                                    coinType = chain.coinType,
                                    denom = chain.feeUnit,
                                    gasLimit = CosmosHelper.getChainGasLimit(chain),
                                )
                                .getPreSignedImageHash(payload)
                        }
                    }

                    Chain.MayaChain -> {
                        val mayaChainHelper = ThorChainHelper.maya(ecdsaKey, ecdsaChainCode)
                        mayaChainHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Polkadot -> {
                        val dotHelper = PolkadotHelper(eddsaKey)
                        dotHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Bittensor -> {
                        val bittensorHelper = BittensorHelper(eddsaKey)
                        bittensorHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Sui -> {
                        SuiHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Ton -> {
                        TonHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Ripple -> {
                        RippleHelper.getPreSignedImageHash(payload)
                    }

                    Chain.Tron -> {
                        TronHelper(
                                coinType = chain.coinType,
                                vaultHexPublicKey = ecdsaKey,
                                vaultHexChainCode = ecdsaChainCode,
                            )
                            .getPreSignedImageHash(payload)
                    }

                    Chain.Cardano -> {
                        CardanoHelper.getPreSignedImageHash(payload)
                    }
                }
        }

        return messages.sorted()
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        vault: Vault,
        signatures: Map<String, tss.KeysignResponse>,
        nonceAcc: BigInteger,
    ): SignedTransactionResult {
        val chain = keysignPayload.coin.chain
        val (ecdsaKey, ecdsaChainCode) = vault.getEcdsaSigningKey(chain)
        val eddsaKey = vault.getEddsaSigningKey(chain)

        val swapPayload = keysignPayload.swapPayload

        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    return THORChainSwaps(ecdsaKey, ecdsaChainCode, eddsaKey)
                        .getSignedTransaction(
                            swapPayload.data,
                            keysignPayload,
                            signatures,
                            nonceAcc,
                        )
                }

                is SwapPayload.EVM -> {
                    return if (keysignPayload.blockChainSpecific is BlockChainSpecific.Solana)
                        SolanaSwap(eddsaKey)
                            .getSignedTransaction(swapPayload.data, keysignPayload, signatures)
                    else
                        OneInchSwap(ecdsaKey, ecdsaChainCode)
                            .getSignedTransaction(
                                swapPayload.data,
                                keysignPayload,
                                signatures,
                                nonceAcc,
                            )
                }

                is SwapPayload.SwapKit -> {
                    val txType = swapPayload.data.txType
                    // See the matching dispatcher in getKeysignMessages for why the UTXO family is
                    // keyed off `chain` rather than `txType`.
                    return if (SwapKitSwapPayloadJson.isUtxoPsbtTxType(txType)) {
                        when (chain) {
                            Chain.Bitcoin,
                            Chain.Litecoin ->
                                SwapKitBtcSigner(ecdsaKey, ecdsaChainCode, chain.coinType)
                                    .getSignedTransaction(swapPayload.data.txPayload, signatures)
                            Chain.BitcoinCash,
                            Chain.Dogecoin,
                            Chain.Dash ->
                                SwapKitLegacyP2PKHSigner(ecdsaKey, ecdsaChainCode, chain.coinType)
                                    .getSignedTransaction(
                                        psbtBytes = swapPayload.data.txPayload,
                                        targetAddress = swapPayload.data.targetAddress,
                                        fromAmount = swapPayload.data.fromAmount,
                                        signatures = signatures,
                                    )
                            Chain.Zcash ->
                                SwapKitZcashSigner(ecdsaKey, ecdsaChainCode)
                                    .getSignedTransaction(
                                        psbtBytes = swapPayload.data.txPayload,
                                        targetAddress = swapPayload.data.targetAddress,
                                        fromAmount = swapPayload.data.fromAmount,
                                        signatures = signatures,
                                        zcashBranchId = keysignPayload.zcashBranchId,
                                    )
                            else -> error("Unsupported SwapKit txType for signing: $txType")
                        }
                    } else {
                        when (txType) {
                            SwapKitSwapPayloadJson.TX_TYPE_TRON ->
                                SwapKitTronSigner(ecdsaKey, ecdsaChainCode)
                                    .getSignedTransaction(swapPayload.data.txPayload, signatures)
                            SwapKitSwapPayloadJson.TX_TYPE_SUI ->
                                SwapKitSuiSigner(eddsaKey)
                                    .getSignedTransaction(swapPayload.data.txPayload, signatures)
                            SwapKitSwapPayloadJson.TX_TYPE_CARDANO_PREBUILT ->
                                SwapKitCardanoSigner(eddsaKey)
                                    .getSignedTransaction(swapPayload.data.txPayload, signatures)
                            // Deposit-only Cardano: build & sign a plain ADA send to targetAddress
                            // via the native Cardano path (same as a non-swap send).
                            SwapKitSwapPayloadJson.TX_TYPE_CARDANO ->
                                CardanoHelper.getSignedTransaction(
                                    vaultHexPublicKey = eddsaKey,
                                    vaultHexChainCode = vault.hexChainCode,
                                    keysignPayload = keysignPayload,
                                    signatures = signatures,
                                )
                            // TON SwapKit reuses the native TonHelper path (EdDSA), signing the
                            // deposit transfer off the KeysignPayload's toAddress / toAmount —
                            // matching iOS.
                            SwapKitSwapPayloadJson.TX_TYPE_TON ->
                                TonHelper.getSignedTransaction(
                                    vaultHexPublicKey = eddsaKey,
                                    payload = keysignPayload,
                                    signatures = signatures,
                                )
                            // XRP deposit-only: rebuild + sign a plain XRP Payment off the
                            // KeysignPayload (toAddress / toAmount / memo) via the native
                            // RippleHelper.
                            SwapKitSwapPayloadJson.TX_TYPE_XRP ->
                                RippleHelper.getSignedTransaction(
                                    keysignPayload = keysignPayload,
                                    signatures = signatures,
                                )
                            else -> error("Unsupported SwapKit txType for signing: $txType")
                        }
                    }
                }
                else -> {}
            }
        } else if (swapPayload is SwapPayload.MayaChain && !swapPayload.srcToken.isNativeToken) {
            return THORChainSwaps(ecdsaKey, ecdsaChainCode, eddsaKey)
                .getSignedTransaction(swapPayload.data, keysignPayload, signatures, nonceAcc)
        }

        // we could define an interface to make the following more simpler,but I will leave it for
        // later
        when (chain) {
            Chain.Bitcoin,
            Chain.Dash,
            Chain.BitcoinCash,
            Chain.Dogecoin,
            Chain.Litecoin,
            Chain.Zcash -> {
                // PSBT co-signing (`signBitcoin != null`) always skips broadcast — only the
                // orchestrating dApp can finalize the segwit transaction — so this branch is
                // only reached for the WalletCore-built path.
                val utxo = UtxoHelper(keysignPayload.coin.coinType, ecdsaKey, ecdsaChainCode)
                return utxo.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.ThorChain -> {
                val thorHelper = ThorChainHelper.thor(ecdsaKey, ecdsaChainCode)
                return thorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Qbtc -> {
                return QBTCTransactionHelper().getSignedTransaction(keysignPayload, signatures)
            }

            Chain.GaiaChain,
            Chain.Kujira,
            Chain.Dydx,
            Chain.Osmosis,
            Chain.Noble,
            Chain.Akash -> {
                return CosmosHelper(
                        coinType = chain.coinType,
                        denom = chain.feeUnit,
                        gasLimit = CosmosHelper.getChainGasLimit(chain),
                    )
                    .getSignedTransaction(keysignPayload, signatures)
            }

            Chain.TerraClassic,
            Chain.Terra -> {
                // Mirror the getPreSignedImageHash routing: a dApp-supplied Cosmos tx (signDirect
                // SignDoc or amino JSON) must be signed + assembled by CosmosHelper, not rebuilt as
                // a bank send by TerraHelper.
                return if (keysignPayload.carriesDappCosmosTx) {
                    CosmosHelper(
                            coinType = chain.coinType,
                            denom = chain.feeUnit,
                            gasLimit = CosmosHelper.getChainGasLimit(chain),
                        )
                        .getSignedTransaction(keysignPayload, signatures)
                } else {
                    TerraHelper(
                            coinType = chain.coinType,
                            denom = chain.feeUnit,
                            gasLimit = CosmosHelper.getChainGasLimit(chain),
                        )
                        .getSignedTransaction(keysignPayload, signatures)
                }
            }

            Chain.Solana -> {
                val solanaHelper = SolanaHelper(eddsaKey)
                return solanaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Ethereum,
            Chain.Avalanche,
            Chain.BscChain,
            Chain.CronosChain,
            Chain.Blast,
            Chain.Mantle,
            Chain.Arbitrum,
            Chain.Optimism,
            Chain.Sei,
            Chain.Polygon,
            Chain.Base,
            Chain.ZkSync,
            Chain.Hyperliquid -> {
                if (keysignPayload.coin.isNativeToken) {
                    val evmHelper =
                        EvmHelper(keysignPayload.coin.coinType, ecdsaKey, ecdsaChainCode)
                    return evmHelper.getSignedTransaction(keysignPayload, signatures)
                } else {
                    val erc20Helper =
                        ERC20Helper(keysignPayload.coin.coinType, ecdsaKey, ecdsaChainCode)
                    return erc20Helper.getSignedTransaction(keysignPayload, signatures)
                }
            }

            Chain.MayaChain -> {
                val mayaHelper = ThorChainHelper.maya(ecdsaKey, ecdsaChainCode)
                return mayaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Polkadot -> {
                val dotHelper = PolkadotHelper(eddsaKey)
                return dotHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Bittensor -> {
                val bittensorHelper = BittensorHelper(eddsaKey)
                return bittensorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Sui -> {
                return SuiHelper.getSignedTransaction(eddsaKey, keysignPayload, signatures)
            }

            Chain.Ton -> {
                return TonHelper.getSignedTransaction(
                    vaultHexPublicKey = eddsaKey,
                    payload = keysignPayload,
                    signatures = signatures,
                )
            }

            Chain.Ripple -> {
                return RippleHelper.getSignedTransaction(
                    keysignPayload = keysignPayload,
                    signatures = signatures,
                )
            }

            Chain.Tron -> {
                return TronHelper(
                        coinType = chain.coinType,
                        vaultHexPublicKey = ecdsaKey,
                        vaultHexChainCode = ecdsaChainCode,
                    )
                    .getSignedTransaction(keysignPayload = keysignPayload, signatures = signatures)
            }

            Chain.Cardano -> {
                return CardanoHelper.getSignedTransaction(
                    vaultHexPublicKey = eddsaKey,
                    vaultHexChainCode = vault.hexChainCode,
                    keysignPayload = keysignPayload,
                    signatures = signatures,
                )
            }
        }
    }
}
