@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.swapAggregators.OneInchSwap
import com.vultisig.wallet.data.common.isHex
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getEcdsaSigningKey
import com.vultisig.wallet.data.models.getEddsaSigningKey
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import vultisig.keysign.v1.CustomMessagePayload
import wallet.core.jni.EthereumAbi

object SigningHelper {
    private const val ETH_SIGN_TYPED_DATA_V4 = "eth_signTypedData_v4"
    // SwapKit `meta.txType` for the Bitcoin PSBT signer dispatch.
    private const val PSBT_TX_TYPE = "PSBT"

    @OptIn(ExperimentalStdlibApi::class)
    fun getKeysignMessages(messagePayload: CustomMessagePayload): List<String> {
        if (messagePayload.method.equals(ETH_SIGN_TYPED_DATA_V4, ignoreCase = true)) {
            return getKeysignMessagesForTypedData(messagePayload.message)
        }

        val processedBytes =
            if (messagePayload.message.isHex()) {
                messagePayload.message.toHexBytes()
            } else {
                messagePayload.message.toByteArray()
            }
        val isEddsa =
            messagePayload.chain?.let { raw ->
                runCatching { Chain.fromRaw(raw).TssKeysignType == TssKeyType.EDDSA }
                    .getOrDefault(false)
            } ?: false
        // EdDSA chains (e.g. TON) deliver the precomputed digest directly; signing it again
        // through keccak256 would diverge from the initiator's hash list.
        val bytes = if (isEddsa) processedBytes else processedBytes.toKeccak256ByteArray()
        return listOf(bytes.toHexString())
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getKeysignMessagesForTypedData(message: String): List<String> {
        val hash = EthereumAbi.encodeTyped(message)
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
                    require(swapPayload.data.txType == PSBT_TX_TYPE) {
                        "Unsupported SwapKit txType for signing: ${swapPayload.data.txType}"
                    }
                    messages +=
                        SwapKitBtcSigner(ecdsaKey, ecdsaChainCode)
                            .getPreSignedImageHash(
                                psbtBytes = swapPayload.data.txPayload,
                                targetAddress = swapPayload.data.targetAddress,
                                fromAmount = swapPayload.data.fromAmount,
                            )
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
                        TerraHelper(
                                coinType = chain.coinType,
                                denom = chain.feeUnit,
                                gasLimit = CosmosHelper.getChainGasLimit(chain),
                            )
                            .getPreSignedImageHash(payload)
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
                    require(swapPayload.data.txType == PSBT_TX_TYPE) {
                        "Unsupported SwapKit txType for signing: ${swapPayload.data.txType}"
                    }
                    return SwapKitBtcSigner(ecdsaKey, ecdsaChainCode)
                        .getSignedTransaction(swapPayload.data.txPayload, signatures)
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
                return TerraHelper(
                        coinType = chain.coinType,
                        denom = chain.feeUnit,
                        gasLimit = CosmosHelper.getChainGasLimit(chain),
                    )
                    .getSignedTransaction(keysignPayload, signatures)
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
