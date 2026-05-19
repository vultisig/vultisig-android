package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.CosmosBankCoinFinder
import com.vultisig.wallet.data.usecases.EvmCoinFinder
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface TokenRepository {

    suspend fun getToken(tokenId: String): Coin?

    suspend fun getNativeToken(chainId: String): Coin

    suspend fun getEVMTokenByContract(chainId: String, contractAddress: String): Coin?

    suspend fun getTokensWithBalance(
        chain: Chain,
        address: String,
        enabledDenoms: Set<String> = emptySet(),
    ): List<Coin>

    suspend fun getRefreshTokens(chain: Chain, vault: Vault): List<Coin>

    val builtInTokens: Flow<List<Coin>>

    val nativeTokens: Flow<List<Coin>>
}

internal class TokenRepositoryImpl
@Inject
constructor(
    private val evmApiFactory: EvmApiFactory,
    private val thorApi: ThorChainApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val evmCoinFinder: EvmCoinFinder,
    private val cosmosBankCoinFinder: CosmosBankCoinFinder,
) : TokenRepository {

    override suspend fun getToken(tokenId: String): Coin? =
        builtInTokens
            .map { allTokens -> allTokens.firstOrNull { it.id.equals(tokenId, ignoreCase = true) } }
            .firstOrNull()

    override suspend fun getNativeToken(chainId: String): Coin =
        nativeTokens.map { it -> it.first { it.chain.id == chainId } }.first()

    override suspend fun getEVMTokenByContract(chainId: String, contractAddress: String): Coin? {
        val chain = Chain.fromRaw(chainId)
        val rpcResponses = evmApiFactory.createEvmApi(chain).findCustomToken(contractAddress)
        if (rpcResponses.isEmpty()) return null
        var ticker = ""
        var decimal = 0
        rpcResponses.forEach {
            val result = it.result ?: return null
            if (it.id == CUSTOM_TOKEN_RESPONSE_TICKER_ID)
                ticker = result.decodeContractString() ?: return null
            else decimal = result.decodeContractDecimal().takeIf { dec -> dec != 0 } ?: return null
        }
        val coin =
            Coin(
                chain = chain,
                ticker = ticker.uppercase(),
                logo = "https://tokens-data.1inch.io/images/$contractAddress.png",
                address = "",
                decimal = decimal,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = contractAddress,
                isNativeToken = false,
            )
        return coin
    }

    override suspend fun getTokensWithBalance(
        chain: Chain,
        address: String,
        enabledDenoms: Set<String>,
    ): List<Coin> {
        return when (chain) {
            Chain.ThorChain -> {
                val balances = thorApi.getBalance(address)
                val metaCache = mutableMapOf<String, DenomMetadata?>()
                balances.mapNotNull {
                    if (it.denom in DEFI_ONLY_THORCHAIN_DENOMS) return@mapNotNull null
                    if (enabledDenoms.isNotEmpty() && it.denom !in enabledDenoms)
                        return@mapNotNull null
                    val metadata =
                        metaCache.getOrPut(it.denom) { thorApi.getDenomMetaFromLCD(it.denom) }

                    var decimal: Int = 8
                    val denom =
                        if (metadata != null) {
                            decimal = decimalsFromMeta(metadata) ?: decimal
                            var denom = deriveTicker(it.denom, metadata)
                            denom
                        } else {
                            it.denom
                        }
                    var symbol = ""

                    if (denom == it.denom) {
                        if (denom.contains(".")) {
                            val parts = denom.split(".")
                            if (parts.size >= 2) {
                                symbol = parts[1].uppercase()
                            }
                        } else if (denom.startsWith("x/nami-index-nav", true)) {
                            // Unfortunately, there is no "yrune" or "tcy" in the denom,
                            // so the only option is to map it manually with actual contract address
                            symbol =
                                when {
                                    denom.lowercase().contains(YRUNE_CONTRACT.lowercase()) ->
                                        "YRUNE"
                                    denom.lowercase().contains(YTCY_CONTRACT.lowercase()) -> "YTCY"
                                    else -> denom
                                }
                        } else if (denom.startsWith("x/", true)) {
                            val parts = denom.split("/")
                            if (parts.size >= 2) {
                                symbol = parts[1].uppercase()
                            }
                        } else if (denom.contains("-")) {
                            val parts = denom.split("-")
                            if (parts.size >= 2) {
                                symbol = parts[1].uppercase()
                            }
                        } else {
                            symbol = denom.uppercase()
                        }
                    } else {
                        symbol = denom.uppercase()
                    }

                    if (denom == "rune") {
                        null
                    } else {
                        Coin(
                            contractAddress = it.denom,
                            chain = chain,
                            ticker = symbol,
                            logo = symbol,
                            decimal = decimal,
                            isNativeToken = false,
                            priceProviderID = "",
                            address = "",
                            hexPublicKey = "",
                        )
                    }
                }
            }
            Chain.Terra,
            Chain.TerraClassic -> cosmosBankCoinFinder.find(chain, address)
            else -> {
                if (chain.standard != TokenStandard.EVM) emptyList()
                else evmCoinFinder.find(chain, address)
            }
        }
    }

    private fun decimalsFromMeta(metadata: DenomMetadata): Int? {
        val denomUnits = metadata.denomUnits ?: return null
        metadata.symbol?.let { symbol ->
            denomUnits
                .firstOrNull { it.denom == symbol && it.exponent != 0 }
                ?.let {
                    return it.exponent
                }
        }
        metadata.display?.let { display ->
            denomUnits
                .firstOrNull { it.denom == display && it.exponent != 0 }
                ?.let {
                    return it.exponent
                }
        }
        return denomUnits.maxByOrNull { it.exponent ?: 0 }?.exponent
    }

    private fun deriveTicker(denom: String, metadata: DenomMetadata): String {
        metadata.symbol
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                return it
            }

        metadata.display
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                return it
            }

        return when {
            denom.startsWith("x/staking-") -> {
                val withoutPrefix = denom.removePrefix("x/staking-")
                "S${withoutPrefix.uppercase()}"
            }
            denom.startsWith("x/") -> {
                denom.split("/").lastOrNull() ?: denom
            }
            denom.startsWith("factory/") -> {
                val lastComponent = denom.split("/").lastOrNull() ?: denom
                if (lastComponent.startsWith("u") && lastComponent.length > 1) {
                    lastComponent.drop(1)
                } else {
                    lastComponent
                }
            }
            else -> denom
        }
    }

    override suspend fun getRefreshTokens(chain: Chain, vault: Vault): List<Coin> {
        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(chain, vault)
        val enabledDenoms =
            if (chain == Chain.ThorChain)
                vault.coins.filter { it.chain == chain }.map { it.contractAddress }.toSet()
            else emptySet()
        return (getTokensWithBalance(chain, address, enabledDenoms) +
                enabledByDefaultTokens.getOrDefault(chain, emptyList()))
            .filterNot { it.isNativeToken }
            .map { token -> token.copy(address = address, hexPublicKey = derivedPublicKey) }
    }

    override val builtInTokens: Flow<List<Coin>> = flowOf(Coins.coins.flatMap { it.value })

    override val nativeTokens: Flow<List<Coin>> = builtInTokens.map { it.filterNatives() }

    private fun Iterable<Coin>.filterNatives() = filter { it.isNativeToken }

    private fun String.decodeContractString(): String? {
        try {
            val bytes = removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val length = BigInteger(bytes.sliceArray(32..63)).toInt()
            return String(bytes.sliceArray(64 until 64 + length)).lowercase()
        } catch (_: Exception) {
            return null
        }
    }

    private fun String.decodeContractDecimal(): Int {
        return BigInteger(removePrefix("0x"), 16).toInt()
    }

    private val enabledByDefaultTokens = listOf(Coins.ThorChain.TCY).groupBy { it.chain }

    companion object {
        private const val CUSTOM_TOKEN_RESPONSE_TICKER_ID = 2

        private const val YRUNE_CONTRACT =
            "thor1mlphkryw5g54yfkrp6xpqzlpv4f8wh6hyw27yyg4z2els8a9gxpqhfhekt"
        private const val YTCY_CONTRACT =
            "thor1h0hr0rm3dawkedh44hlrmgvya6plsryehcr46yda2vj0wfwgq5xqrs86px"
    }
}

// Denoms surfaced under the DeFi tab — must not be auto-discovered as wallet tokens.
internal val DEFI_ONLY_THORCHAIN_DENOMS = setOf("x/staking-ruji")
