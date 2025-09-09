@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.swapAggregators.KyberSwap
import com.vultisig.wallet.data.api.swapAggregators.OneInchSwap
import com.vultisig.wallet.data.common.isHex
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import vultisig.keysign.v1.CustomMessagePayload
import java.math.BigInteger

object SigningHelper {
    @OptIn(ExperimentalStdlibApi::class)
    fun getKeysignMessages(
        messagePayload: CustomMessagePayload
    ): List<String> {
        val processedBytes = if (messagePayload.message.isHex()) {
            messagePayload.message.toHexBytes()
        } else {
            messagePayload.message.toByteArray()
        }
        return listOf(
            processedBytes
                .toKeccak256ByteArray()
                .toHexString()
        )
    }

    fun getKeysignMessages(
        payload: KeysignPayload,
        vault: Vault
    ): List<String> {
        val messages = mutableListOf<String>()

        var nonceAcc = BigInteger.ZERO

        val approvePayload = payload.approvePayload
        if (approvePayload != null) {
            messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getPreSignedApproveImageHash(approvePayload, payload)
            nonceAcc++
        }

        val swapPayload = payload.swapPayload
        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    messages += THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
                }

                is SwapPayload.EVM -> {
                    val message = if (payload.coin.chain == Chain.Solana) {
                        SolanaSwap(vault.pubKeyEDDSA)
                            .getPreSignedImageHash(
                                swapPayload.data,
                                payload
                            )
                    } else OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(
                            swapPayload.data,
                            payload,
                            nonceAcc
                        )

                    messages += message
                }
                is SwapPayload.Kyber -> {
                    messages += KyberSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
                }
                // mayachain is implemented through send transaction
                else -> Unit
            }
        } else {
            val isMayaErc20Swap =
                payload.swapPayload != null
                        && swapPayload is SwapPayload.MayaChain
                        && !payload.coin.isNativeToken
                        && payload.coin.chain.standard == TokenStandard.EVM

            // catch as generic message outside
            if (isMayaErc20Swap) {
                throw UnsupportedOperationException("Not Implemented")
            }
            val chain = payload.coin.chain
            messages += when (chain) {
                Chain.ThorChain -> {
                    val thorHelper = ThorChainHelper.thor(vault.pubKeyECDSA, vault.hexChainCode)
                    thorHelper.getPreSignedImageHash(payload)
                }

                Chain.Solana -> {
                    val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                    solanaHelper.getPreSignedImageHash(payload)
                }

                Chain.Ethereum, Chain.Avalanche, Chain.Base, Chain.Blast, Chain.Arbitrum,Chain.Mantle,
                Chain.Polygon, Chain.Optimism, Chain.BscChain, Chain.CronosChain, Chain.ZkSync -> {
                    if (payload.coin.isNativeToken) {
                        EvmHelper(
                            payload.coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(payload)
                    } else {
                        ERC20Helper(
                            payload.coin.coinType,
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getPreSignedImageHash(payload)
                    }
                }

                Chain.Bitcoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dogecoin, Chain.Dash, Chain.Zcash -> {
                    val utxo =
                        UtxoHelper(payload.coin.coinType, vault.pubKeyECDSA, vault.hexChainCode)
                    utxo.getPreSignedImageHash(payload)
                }

                Chain.GaiaChain, Chain.Kujira, Chain.Dydx, Chain.Osmosis, Chain.Noble, Chain.Akash -> {
                    CosmosHelper(
                        coinType = chain.coinType,
                        denom = chain.feeUnit,
                        gasLimit = CosmosHelper.getChainGasLimit(chain),
                    ).getPreSignedImageHash(payload)
                }

                Chain.Terra, Chain.TerraClassic -> {
                    TerraHelper(
                        coinType = chain.coinType,
                        denom = chain.feeUnit,
                        gasLimit = CosmosHelper.getChainGasLimit(chain),
                    ).getPreSignedImageHash(payload)
                }

                Chain.MayaChain -> {
                    val mayaChainHelper =
                        ThorChainHelper.maya(vault.pubKeyECDSA, vault.hexChainCode)
                    mayaChainHelper.getPreSignedImageHash(payload)
                }

                Chain.Polkadot -> {
                    val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                    dotHelper.getPreSignedImageHash(payload)
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
                        vaultHexPublicKey = vault.pubKeyECDSA,
                        vaultHexChainCode = vault.hexChainCode
                    ).getPreSignedImageHash(payload)
                }

                Chain.Cardano -> {
                    CardanoHelper.getPreSignedImageHash(
                        payload
                    )
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
        val swapPayload = keysignPayload.swapPayload

        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    return THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                        .getSignedTransaction(
                            swapPayload.data,
                            keysignPayload,
                            signatures,
                            nonceAcc
                        )
                }

                is SwapPayload.EVM -> {
                    return if (keysignPayload.blockChainSpecific is BlockChainSpecific.Solana)
                        SolanaSwap(vault.pubKeyEDDSA)
                            .getSignedTransaction(
                                swapPayload.data, keysignPayload, signatures
                            )
                    else OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getSignedTransaction(
                            swapPayload.data,
                            keysignPayload,
                            signatures,
                            nonceAcc
                        )
                }

                is SwapPayload.Kyber -> {
                    return KyberSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getSignedTransaction(
                            swapPayload.data,
                            keysignPayload,
                            signatures,
                            nonceAcc
                        )
                }

                else -> {}
            }

        }

        val chain = keysignPayload.coin.chain
        // we could define an interface to make the following more simpler,but I will leave it for later
        when (keysignPayload.coin.chain) {
            Chain.Bitcoin, Chain.Dash, Chain.BitcoinCash, Chain.Dogecoin, Chain.Litecoin, Chain.Zcash -> {
                val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)
                return utxo.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.ThorChain -> {
                val thorHelper = ThorChainHelper.thor(vault.pubKeyECDSA, vault.hexChainCode)
                return thorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.GaiaChain, Chain.Kujira, Chain.Dydx, Chain.Osmosis, Chain.Noble, Chain.Akash -> {
                return CosmosHelper(
                    coinType = chain.coinType,
                    denom = chain.feeUnit,
                    gasLimit = CosmosHelper.getChainGasLimit(chain),
                ).getSignedTransaction(keysignPayload, signatures)
            }

            Chain.TerraClassic, Chain.Terra -> {
                return TerraHelper(
                    coinType = chain.coinType,
                    denom = chain.feeUnit,
                    gasLimit = CosmosHelper.getChainGasLimit(chain),
                ).getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Solana -> {
                val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                return solanaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Ethereum, Chain.Avalanche, Chain.BscChain, Chain.CronosChain, Chain.Blast,Chain.Mantle,
            Chain.Arbitrum, Chain.Optimism, Chain.Polygon, Chain.Base, Chain.ZkSync -> {
                if (keysignPayload.coin.isNativeToken) {
                    val evmHelper = EvmHelper(
                        keysignPayload.coin.coinType,
                        vault.pubKeyECDSA,
                        vault.hexChainCode
                    )
                    return evmHelper.getSignedTransaction(keysignPayload, signatures)
                } else {
                    val erc20Helper = ERC20Helper(
                        keysignPayload.coin.coinType,
                        vault.pubKeyECDSA,
                        vault.hexChainCode
                    )
                    return erc20Helper.getSignedTransaction(keysignPayload, signatures)
                }

            }

            Chain.MayaChain -> {
                val mayaHelper = ThorChainHelper.maya(vault.pubKeyECDSA, vault.hexChainCode)
                return mayaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Polkadot -> {
                val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                return dotHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Sui -> {
                return SuiHelper.getSignedTransaction(
                    vault.pubKeyEDDSA,
                    keysignPayload, signatures
                )
            }

            Chain.Ton -> {
                return TonHelper.getSignedTransaction(
                    vaultHexPublicKey = vault.pubKeyEDDSA,
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
                    vaultHexPublicKey = vault.pubKeyECDSA,
                    vaultHexChainCode = vault.hexChainCode
                ).getSignedTransaction(
                    keysignPayload = keysignPayload,
                    signatures = signatures,
                )
            }

            Chain.Cardano -> {
                return CardanoHelper.getSignedTransaction(
                    vaultHexPublicKey = vault.pubKeyEDDSA,
                    vaultHexChainCode = vault.hexChainCode,
                    keysignPayload = keysignPayload,
                    signatures = signatures,
                )
            }
        }


    }
}