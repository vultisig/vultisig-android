package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.chains.UtxoInfo
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchSwapTxJson
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.models.proto.v1.ThorChainSwapPayloadProto
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

internal interface KeysignPayloadProtoMapper :
    MapperFunc<KeysignPayloadProto, KeysignPayload>

internal class KeysignPayloadProtoMapperImpl @Inject constructor() : KeysignPayloadProtoMapper {

    override fun invoke(from: KeysignPayloadProto): KeysignPayload {
        return KeysignPayload(
            vaultLocalPartyID = from.vaultLocalPartyId,
            vaultPublicKeyECDSA = from.vaultPublicKeyEcdsa,
            toAddress = from.toAddress,
            toAmount = BigInteger(from.toAmount),
            memo = from.memo,
            coin = requireNotNull(from.coin).toCoin(),
            utxos = from.utxoInfo
                .asSequence()
                .filterNotNull()
                .map {
                    UtxoInfo(
                        hash = it.hash,
                        amount = it.amount,
                        index = it.index,
                    )
                }
                .toList(),
            approvePayload = from.erc20ApprovePayload?.let {
                ERC20ApprovePayload(
                    amount = BigInteger(it.amount),
                    spender = it.spender,
                )
            },
            swapPayload = when {
                from.oneinchSwapPayload != null -> from.oneinchSwapPayload.let {
                    SwapPayload.OneInch(
                        OneInchSwapPayloadJson(
                            fromCoin = requireNotNull(it.fromCoin).toCoin(),
                            toCoin = requireNotNull(it.toCoin).toCoin(),
                            fromAmount = BigInteger(it.fromAmount),
                            toAmountDecimal = BigDecimal(it.toAmountDecimal),
                            quote = requireNotNull(it.quote).let {
                                OneInchSwapQuoteJson(
                                    dstAmount = it.dstAmount,
                                    tx = requireNotNull(it.tx).let {
                                        OneInchSwapTxJson(
                                            from = it.from,
                                            to = it.to,
                                            gas = it.gas,
                                            data = it.data,
                                            value = it.value,
                                            gasPrice = it.gasPrice,
                                        )
                                    },
                                )
                            },
                        )
                    )
                }

                from.thorchainSwapPayload != null -> from.thorchainSwapPayload.let {
                    SwapPayload.ThorChain(
                        it.toThorChainSwapPayload()
                    )
                }

                from.mayachainSwapPayload != null -> from.mayachainSwapPayload.let {
                    SwapPayload.MayaChain(
                        it.toThorChainSwapPayload()
                    )
                }

                else -> null
            },

            blockChainSpecific = when {
                from.ethereumSpecific != null -> from.ethereumSpecific.let {
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger(it.maxFeePerGasWei),
                        priorityFeeWei = BigInteger(it.priorityFee),
                        nonce = it.nonce.toBigInteger(),
                        gasLimit = BigInteger(it.gasLimit),
                    )
                }

                from.thorchainSpecific != null -> from.thorchainSpecific.let {
                    BlockChainSpecific.THORChain(
                        accountNumber = BigInteger(it.accountNumber.toString()),
                        sequence = BigInteger(it.sequence.toString()),
                        fee = BigInteger(it.fee.toString()),
                    )
                }

                from.utxoSpecific != null -> from.utxoSpecific.let {
                    BlockChainSpecific.UTXO(
                        byteFee = BigInteger(it.byteFee),
                        sendMaxAmount = it.sendMaxAmount,
                    )
                }

                from.mayaSpecific != null -> from.mayaSpecific.let {
                    BlockChainSpecific.MayaChain(
                        accountNumber = BigInteger(it.accountNumber.toString()),
                        sequence = BigInteger(it.sequence.toString()),
                    )
                }

                from.cosmosSpecific != null -> from.cosmosSpecific.let {
                    BlockChainSpecific.Cosmos(
                        accountNumber = BigInteger(it.accountNumber.toString()),
                        sequence = BigInteger(it.sequence.toString()),
                        gas = BigInteger(it.gas.toString()),
                    )
                }

                from.solanaSpecific != null -> from.solanaSpecific.let {
                    BlockChainSpecific.Solana(
                        recentBlockHash = it.recentBlockHash,
                        priorityFee = BigInteger(it.priorityFee),
                        fromAddressPubKey = it.fromTokenAssociatedAddress,
                        toAddressPubKey = it.toTokenAssociatedAddress,
                    )
                }

                from.polkadotSpecific != null -> from.polkadotSpecific.let {
                    BlockChainSpecific.Polkadot(
                        recentBlockHash = it.recentBlockHash,
                        nonce = BigInteger(it.nonce.toString()),
                        currentBlockNumber = BigInteger(it.currentBlockNumber),
                        specVersion = it.specVersion,
                        transactionVersion = it.transactionVersion,
                        genesisHash = it.genesisHash,
                    )
                }

                from.suicheSpecific != null -> from.suicheSpecific.let {
                    BlockChainSpecific.Sui(
                        referenceGasPrice = BigInteger(it.referenceGasPrice),
                        coins = TODO("Sui is not implemented") // TODO
                    )
                }

                else -> error("No supported BlockChainSpecific in proto $from")
            },
        )
    }

    private fun CoinProto.toCoin(): Coin = Coin(
        chain = Chain.fromRaw(chain),
        ticker = ticker,
        address = address,
        contractAddress = contractAddress,
        hexPublicKey = hexPublicKey,
        decimal = decimals,
        priceProviderID = priceProviderId,
        logo = logo,
        isNativeToken = isNativeToken,
    )

    private fun ThorChainSwapPayloadProto.toThorChainSwapPayload() =
        THORChainSwapPayload(
            fromAddress = fromAddress,
            fromCoin = requireNotNull(fromCoin).toCoin(),
            toCoin = requireNotNull(toCoin).toCoin(),
            vaultAddress = vaultAddress,
            routerAddress = routerAddress,
            fromAmount = BigInteger(fromAmount),
            toAmountDecimal = BigDecimal(toAmountDecimal),
            toAmountLimit = toAmountLimit,
            steamingInterval = streamingInterval,
            streamingQuantity = streamingQuantity,
            expirationTime = expirationTime,
            isAffiliate = isAffiliate,
        )

}