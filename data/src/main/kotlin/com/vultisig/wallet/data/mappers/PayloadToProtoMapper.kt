package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.models.toProtoString
import javax.inject.Inject
import vultisig.keysign.v1.CardanoChainSpecific
import vultisig.keysign.v1.CosmosSpecific
import vultisig.keysign.v1.Erc20ApprovePayload
import vultisig.keysign.v1.EthereumSpecific
import vultisig.keysign.v1.MAYAChainSpecific
import vultisig.keysign.v1.OneInchQuote
import vultisig.keysign.v1.OneInchSwapPayload
import vultisig.keysign.v1.OneInchTransaction
import vultisig.keysign.v1.PolkadotSpecific
import vultisig.keysign.v1.RippleSpecific
import vultisig.keysign.v1.SolanaSpecific
import vultisig.keysign.v1.SuiSpecific
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.TonSpecific
import vultisig.keysign.v1.TronSpecific
import vultisig.keysign.v1.UTXOSpecific

interface PayloadToProtoMapper : MapperFunc<KeysignPayload?, KeysignPayloadProto?>

internal class PayloadToProtoMapperImpl @Inject constructor() : PayloadToProtoMapper {

    override fun invoke(keysignPayload: KeysignPayload?): KeysignPayloadProto? {
        keysignPayload ?: return null
        val swapPayload = keysignPayload.swapPayload
        val approvePayload = keysignPayload.approvePayload
        val specific = keysignPayload.blockChainSpecific
        return KeysignPayloadProto(
            coin = keysignPayload.coin.toCoinProto(),
            toAddress = keysignPayload.toAddress,
            toAmount = keysignPayload.toAmount.toString(),
            memo = keysignPayload.memo,
            vaultLocalPartyId = keysignPayload.vaultLocalPartyID,
            vaultPublicKeyEcdsa = keysignPayload.vaultPublicKeyECDSA,
            libType = keysignPayload.libType?.toProtoString() ?: "",
            utxoSpecific =
                if (specific is BlockChainSpecific.UTXO) {
                    UTXOSpecific(
                        byteFee = specific.byteFee.toString(),
                        sendMaxAmount = specific.sendMaxAmount,
                    )
                } else null,
            utxoInfo =
                keysignPayload.utxos.map {
                    vultisig.keysign.v1.UtxoInfo(
                        hash = it.hash,
                        amount = it.amount,
                        index = it.index,
                    )
                },
            ethereumSpecific =
                if (specific is BlockChainSpecific.Ethereum) {
                    EthereumSpecific(
                        maxFeePerGasWei = specific.maxFeePerGasWei.toString(),
                        priorityFee = specific.priorityFeeWei.toString(),
                        nonce = specific.nonce.toLong(),
                        gasLimit = specific.gasLimit.toString(),
                    )
                } else null,
            thorchainSpecific =
                if (specific is BlockChainSpecific.THORChain) {
                    THORChainSpecific(
                        accountNumber = specific.accountNumber.toString().toULong(),
                        sequence = specific.sequence.toString().toULong(),
                        fee = specific.fee.toString().toULong(),
                        isDeposit = specific.isDeposit,
                        transactionType = specific.transactionType,
                    )
                } else null,
            mayaSpecific =
                if (specific is BlockChainSpecific.MayaChain) {
                    MAYAChainSpecific(
                        accountNumber = specific.accountNumber.toString().toULong(),
                        sequence = specific.sequence.toString().toULong(),
                        isDeposit = specific.isDeposit,
                    )
                } else null,
            cosmosSpecific =
                if (specific is BlockChainSpecific.Cosmos) {
                    CosmosSpecific(
                        accountNumber = specific.accountNumber.toString().toULong(),
                        sequence = specific.sequence.toString().toULong(),
                        gas = specific.gas.toString().toULong(),
                        transactionType = specific.transactionType,
                        ibcDenomTraces = specific.ibcDenomTraces,
                        gasLimit = specific.gasLimit?.toString()?.toULong(),
                    )
                } else null,
            solanaSpecific =
                if (specific is BlockChainSpecific.Solana) {
                    SolanaSpecific(
                        recentBlockHash = specific.recentBlockHash,
                        priorityFee = specific.priorityFee.toString(),
                        toTokenAssociatedAddress = specific.toAddressPubKey,
                        fromTokenAssociatedAddress = specific.fromAddressPubKey,
                        programId = specific.programId,
                        computeLimit = specific.priorityLimit.toString(),
                    )
                } else null,
            polkadotSpecific =
                if (specific is BlockChainSpecific.Polkadot) {
                    PolkadotSpecific(
                        recentBlockHash = specific.recentBlockHash,
                        nonce = specific.nonce.toString().toULong(),
                        currentBlockNumber = specific.currentBlockNumber.toString(),
                        specVersion = specific.specVersion,
                        transactionVersion = specific.transactionVersion,
                        genesisHash = specific.genesisHash,
                    )
                } else null,
            suicheSpecific =
                if (specific is BlockChainSpecific.Sui) {
                    SuiSpecific(
                        referenceGasPrice = specific.referenceGasPrice.toString(),
                        coins = specific.coins,
                        gasBudget = specific.gasBudget.toString(),
                    )
                } else null,
            rippleSpecific =
                if (specific is BlockChainSpecific.Ripple) {
                    RippleSpecific(
                        sequence = specific.sequence,
                        lastLedgerSequence = specific.lastLedgerSequence,
                        gas = specific.gas,
                    )
                } else null,
            tonSpecific =
                if (specific is BlockChainSpecific.Ton) {
                    TonSpecific(
                        sequenceNumber = specific.sequenceNumber,
                        expireAt = specific.expireAt,
                        bounceable = specific.bounceable,
                        sendMaxAmount = specific.sendMaxAmount,
                        jettonAddress = specific.jettonAddress,
                        isActiveDestination = specific.isActiveDestination,
                    )
                } else null,
            tronSpecific =
                if (specific is BlockChainSpecific.Tron) {
                    TronSpecific(
                        timestamp = specific.timestamp,
                        expiration = specific.expiration,
                        blockHeaderTimestamp = specific.blockHeaderTimestamp,
                        blockHeaderNumber = specific.blockHeaderNumber,
                        blockHeaderVersion = specific.blockHeaderVersion,
                        blockHeaderTxTrieRoot = specific.blockHeaderTxTrieRoot,
                        blockHeaderParentHash = specific.blockHeaderParentHash,
                        blockHeaderWitnessAddress = specific.blockHeaderWitnessAddress,
                        gasEstimation = specific.gasFeeEstimation,
                    )
                } else null,
            cardano =
                if (specific is BlockChainSpecific.Cardano) {
                    CardanoChainSpecific(
                        byteFee = specific.byteFee,
                        sendMaxAmount = specific.sendMaxAmount,
                        ttl = specific.ttl,
                    )
                } else null,
            thorchainSwapPayload =
                if (swapPayload is SwapPayload.ThorChain) {
                    val from = swapPayload.data
                    vultisig.keysign.v1.THORChainSwapPayload(
                        fromAddress = from.fromAddress,
                        fromCoin = from.fromCoin.toCoinProto(),
                        toCoin = from.toCoin.toCoinProto(),
                        vaultAddress = from.vaultAddress,
                        routerAddress = from.routerAddress,
                        fromAmount = from.fromAmount.toString(),
                        toAmountDecimal = from.toAmountDecimal.toPlainString(),
                        toAmountLimit = from.toAmountLimit,
                        streamingInterval = from.streamingInterval,
                        streamingQuantity = from.streamingQuantity,
                        expirationTime = from.expirationTime,
                        isAffiliate = from.isAffiliate,
                    )
                } else null,
            mayachainSwapPayload =
                if (swapPayload is SwapPayload.MayaChain) {
                    val from = swapPayload.data
                    vultisig.keysign.v1.THORChainSwapPayload(
                        fromAddress = from.fromAddress,
                        fromCoin = from.fromCoin.toCoinProto(),
                        toCoin = from.toCoin.toCoinProto(),
                        vaultAddress = from.vaultAddress,
                        routerAddress = from.routerAddress,
                        fromAmount = from.fromAmount.toString(),
                        toAmountDecimal = from.toAmountDecimal.toPlainString(),
                        toAmountLimit = from.toAmountLimit,
                        streamingInterval = from.streamingInterval,
                        streamingQuantity = from.streamingQuantity,
                        expirationTime = from.expirationTime,
                        isAffiliate = from.isAffiliate,
                    )
                } else null,
            oneinchSwapPayload =
                if (swapPayload is SwapPayload.EVM) {
                    val from = swapPayload.data
                    OneInchSwapPayload(
                        fromCoin = from.fromCoin.toCoinProto(),
                        toCoin = from.toCoin.toCoinProto(),
                        fromAmount = from.fromAmount.toString(),
                        toAmountDecimal = from.toAmountDecimal.toPlainString(),
                        quote =
                            from.quote.let { it ->
                                OneInchQuote(
                                    dstAmount = it.dstAmount,
                                    tx =
                                        it.tx.let {
                                            OneInchTransaction(
                                                from = it.from,
                                                to = it.to,
                                                `data` = it.data,
                                                `value` = it.value,
                                                gasPrice = it.gasPrice,
                                                gas = it.gas,
                                                swapFee = it.swapFee,
                                                swapFeeChain = it.swapFeeChain.ifEmpty { null },
                                                swapFeeTokenId =
                                                    it.swapFeeTokenContract.ifEmpty { null },
                                                swapFeeDecimals = it.swapFeeDecimals,
                                            )
                                        },
                                )
                            },
                        provider = from.provider,
                    )
                } else null,
            // EVM/Solana SwapKit ride oneinchSwapPayload above with provider="swapkit"; this
            // carries non-EVM shapes only.
            swapkitSwapPayload =
                if (swapPayload is SwapPayload.SwapKit) {
                    val from = swapPayload.data
                    vultisig.keysign.v1.SwapKitSwapPayload(
                        fromCoin = from.fromCoin.toCoinProto(),
                        toCoin = from.toCoin.toCoinProto(),
                        fromAmount = from.fromAmount.toString(),
                        toAmountDecimal = from.toAmountDecimal.toPlainString(),
                        txType = from.txType,
                        txPayload = from.txPayload,
                        targetAddress = from.targetAddress,
                        inboundAddress = from.inboundAddress,
                        memo = from.memo,
                        subProvider = from.subProvider,
                        swapId = from.swapId,
                    )
                } else null,
            wasmExecuteContractPayload = keysignPayload.wasmExecuteContractPayload,
            // Cosmos SignDoc artefacts MUST round-trip to peer devices. Without these, a relayed
            // payload arrives with `signDirect`/`signAmino == null` on the peer, which then
            // rebuilds
            // a default (bank-send) signing input — its message hash diverges from the initiator's,
            // so the DKLS setup message (keyed by md5(hash)) 404s and keysign never completes. The
            // inbound [KeysignPayloadProtoMapper] already reads both fields; this makes the mapping
            // symmetric. Required for the LUNA / LUNC staking flows (signDirect = MsgDelegate / …
            // ).
            signAmino = keysignPayload.signAmino,
            signDirect = keysignPayload.signDirect,
            // Same round-trip requirement as signAmino/signDirect above: these carry the
            // pre-built, byte-parity signing artefacts (Solana native-staking raw tx, TON/Sui/
            // Bitcoin sign data). Without relaying them, a co-signer receives them as null and
            // rebuilds a default (plain-transfer) signing input — its message hash diverges from
            // the initiator's, so the DKLS setup message 404s and keysign never completes. The
            // inbound [KeysignPayloadProtoMapper] already reads all four; this makes it symmetric.
            // Required for multi-device (secure vault) Solana staking (delegate / unstake / move /
            // finish-move / withdraw).
            signSolana = keysignPayload.signSolana,
            signTon = keysignPayload.signTon,
            signSui = keysignPayload.signSui,
            signBitcoin = keysignPayload.signBitcoin,
            erc20ApprovePayload =
                if (approvePayload is ERC20ApprovePayload) {
                    Erc20ApprovePayload(
                        spender = approvePayload.spender,
                        amount = approvePayload.amount.toString(),
                    )
                } else null,
            skipBroadcast = keysignPayload.skipBroadcast,
            isQbtcClaim = keysignPayload.isQbtcClaim,
            tronTransferContractPayload = keysignPayload.tronTransferContractPayload,
            tronTransferAssetContractPayload = keysignPayload.tronTransferAssetContractPayload,
            tronTriggerSmartContractPayload = keysignPayload.tronTriggerSmartContractPayload,
            // Symmetric with the inbound mapper: when Android relays a payload (e.g. as the
            // initiator for a future dApp-initiated flow) the dApp identity must round-trip to the
            // peers, matching iOS `mapToProtobuff` and Windows `buildDappMetadata`.
            dappMetadata =
                keysignPayload.dappMetadata?.let {
                    vultisig.keysign.v1.DAppMetadata(
                        name = it.name,
                        url = it.url,
                        iconUrl = it.iconUrl,
                    )
                },
        )
    }

    private fun Coin.toCoinProto() =
        CoinProto(
            chain = chain.raw,
            ticker = ticker,
            address = address,
            contractAddress = contractAddress,
            decimals = decimal,
            priceProviderId = priceProviderID,
            isNativeToken = isNativeToken,
            hexPublicKey = hexPublicKey,
            logo = logo,
        )
}
