@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.wallet.OneInchSwap
import vultisig.keysign.v1.CustomMessagePayload
import java.math.BigInteger

object SigningHelper {

    fun getKeysignMessages(
        messagePayload: CustomMessagePayload
    ): List<String> = listOf(
        messagePayload.message
            .toByteArray()
            .toKeccak256ByteArray()
            .toHexString()
    )

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

                is SwapPayload.OneInch -> {
                    messages += OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getPreSignedImageHash(swapPayload.data, payload, nonceAcc)
                }
                // mayachain is implemented through send transaction
                else -> Unit
            }
        } else {
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

                Chain.Ethereum, Chain.Avalanche, Chain.Base, Chain.Blast, Chain.Arbitrum,
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

                Chain.Bitcoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dogecoin, Chain.Dash -> {
                    val utxo =
                        UtxoHelper(payload.coin.coinType, vault.pubKeyECDSA, vault.hexChainCode)
                    utxo.getPreSignedImageHash(payload)
                }

                Chain.GaiaChain, Chain.Dydx, Chain.Kujira, Chain.Osmosis, Chain.Noble -> {
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

                is SwapPayload.OneInch -> {
                    return OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
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
            Chain.Bitcoin, Chain.Dash, Chain.BitcoinCash, Chain.Dogecoin, Chain.Litecoin -> {
                val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)
                return utxo.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.ThorChain -> {
                val thorHelper = ThorChainHelper.thor(vault.pubKeyECDSA, vault.hexChainCode)
                return thorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.GaiaChain, Chain.Kujira, Chain.Dydx, Chain.Osmosis, Chain.Noble -> {
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

            Chain.Ethereum, Chain.Avalanche, Chain.BscChain, Chain.CronosChain, Chain.Blast,
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
        }
    }

}