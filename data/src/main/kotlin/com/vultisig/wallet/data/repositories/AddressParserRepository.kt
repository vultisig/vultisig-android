package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.swapAssetName
import com.vultisig.wallet.data.utils.Numeric
import javax.inject.Inject


interface AddressParserRepository {
    suspend fun resolveName(input: String, chain: Chain): String

    suspend fun isEnsNameService(input: String): Boolean
}

internal class AddressParserRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val thorChainApi: ThorChainApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : AddressParserRepository {

    private val supportedEns = listOf(".eth", ".sol")
    override suspend fun resolveName(input: String, chain: Chain): String {
        return when {
            // address is already valid address, let's return it
            chainAccountAddressRepository.isValid(chain, input) ->
                input
            // might be ens name, try to resolve it
            isEnsNameService(input) -> {
                val namehash = input.namehash()
                val factory = evmApiFactory.createEvmApi(chain)
                factory.resolveENS(namehash)
            }
            // try to resolve it as TNS name
            else -> {
                if (input.isNotEmpty()) {
                    thorChainApi.resolveName(input, chain.swapAssetName())
                        ?: error("Failed to resolve address $input for $chain")
                } else {
                    error("Failed to resolve address $input for $chain")
                }
            }
        }
    }

    override suspend fun isEnsNameService(input: String): Boolean {
        return supportedEns.any { input.endsWith(it) }
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

