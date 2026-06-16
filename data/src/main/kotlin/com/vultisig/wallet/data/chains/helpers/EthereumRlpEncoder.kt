package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.toSafeByteArray
import java.math.BigInteger
import wallet.core.jni.CoinType
import wallet.core.jni.EthereumRlp
import wallet.core.jni.proto.EthereumRlp.EncodingInput
import wallet.core.jni.proto.EthereumRlp.EncodingOutput
import wallet.core.jni.proto.EthereumRlp.RlpItem
import wallet.core.jni.proto.EthereumRlp.RlpList

/**
 * Builds the RLP-encoded, **unsigned** EIP-1559 (type-2) transaction payload that OP-stack chains'
 * `GasPriceOracle.getL1Fee(bytes)` expects in order to price the L1 data-availability component of
 * an L2 transaction. The L1 fee scales with the serialized byte size of this payload, so the fields
 * mirror what will actually be broadcast.
 *
 * The envelope is `0x02 || rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to,
 * value, data, accessList])` with an empty access list.
 */
object EthereumRlpEncoder {

    private const val EIP1559_TX_TYPE: Byte = 0x02

    fun encodeUnsignedEip1559(
        chainId: BigInteger,
        nonce: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        gasLimit: BigInteger,
        to: String,
        value: BigInteger,
        data: ByteArray,
    ): ByteArray {
        val txList =
            RlpList.newBuilder()
                .addItems(u256(chainId))
                .addItems(u256(nonce))
                .addItems(u256(maxPriorityFeePerGas))
                .addItems(u256(maxFeePerGas))
                .addItems(u256(gasLimit))
                .addItems(RlpItem.newBuilder().setAddress(to))
                .addItems(u256(value))
                .addItems(RlpItem.newBuilder().setData(ByteString.copyFrom(data)))
                // empty access list
                .addItems(RlpItem.newBuilder().setList(RlpList.newBuilder()))
                .build()

        val input = EncodingInput.newBuilder().setItem(RlpItem.newBuilder().setList(txList)).build()

        val output =
            EncodingOutput.parseFrom(EthereumRlp.encode(CoinType.ETHEREUM, input.toByteArray()))
        val encoded = output.encoded.toByteArray()

        return ByteArray(encoded.size + 1).apply {
            this[0] = EIP1559_TX_TYPE
            System.arraycopy(encoded, 0, this, 1, encoded.size)
        }
    }

    private fun u256(value: BigInteger): RlpItem.Builder =
        RlpItem.newBuilder().setNumberU256(ByteString.copyFrom(value.toSafeByteArray()))
}
