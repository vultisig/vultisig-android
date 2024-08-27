package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.isENSNameService
import com.vultisig.wallet.common.toKeccak256ByteArray
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.models.Chain
import javax.inject.Inject

val supportedENS = listOf(".eth", ".sol")

internal interface AddressParserRepository {
    suspend fun resolveInput(input: String, chain: Chain): String
}

internal class AddressParserRepositoryImpl @Inject constructor(
    val evmApiFactory: EvmApiFactory,
) : AddressParserRepository {
    override suspend fun resolveInput(input: String, chain: Chain): String {
        return if (input.isENSNameService()) {
            val namehash = input.namehash()
            val factory = evmApiFactory.createEvmApi(chain)
            factory.resolveENS(namehash)
        } else {
            input
        }
    }
}

private fun String.namehash(): String {
    val labels = this.split(".").reversed()
    var node = ByteArray(32) { 0 }

    for (label in labels) {
        val labelData = label.toByteArray(Charsets.UTF_8)
        val labelHash = labelData.toKeccak256ByteArray()
        node = (node + labelHash).toKeccak256ByteArray()
    }

    return Numeric.toHexString(node)
}

