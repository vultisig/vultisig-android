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
                ticker = decodeErc20MetadataString(result) ?: return null
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

                    // The generic derivation uppercases tickers ("BRUNE") and can't map a logo for
                    // Rujira's liquid-bonding denoms. Restore their proper casing + curated logo,
                    // and store the canonical (lowercase) denom so case-sensitive consumers
                    // (isLpToken picker exclusion, contract-based pricing) stay on the right path.
                    var contractAddress = it.denom
                    when (it.denom.lowercase()) {
                        Coins.ThorChain.bRUNE.contractAddress -> {
                            symbol = Coins.ThorChain.bRUNE.ticker
                            contractAddress = Coins.ThorChain.bRUNE.contractAddress
                        }
                        Coins.ThorChain.ybRUNE.contractAddress -> {
                            symbol = Coins.ThorChain.ybRUNE.ticker
                            contractAddress = Coins.ThorChain.ybRUNE.contractAddress
                        }
                    }

                    if (denom == "rune") {
                        null
                    } else {
                        Coin(
                            contractAddress = contractAddress,
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
// The on-chain sRUJI receipt denom is "x/staking-x/ruji"; the legacy "x/staking-ruji" spelling is
// kept defensively so the receipt stays excluded regardless of which the node reports.
internal val DEFI_ONLY_THORCHAIN_DENOMS = setOf("x/staking-ruji", "x/staking-x/ruji")

/**
 * Decodes the result of an ERC-20 `name()` / `symbol()` `eth_call` into text.
 *
 * The standard return is an ABI dynamic `string` (offset word, length word, then the bytes). Some
 * legacy tokens (e.g. MKR) declare `name()`/`symbol()` as `bytes32`; bridged deployments re-encode
 * that as a `string` whose content is the 64-char hex of the original `bytes32`, right-padded with
 * zeros — which would otherwise surface to the UI as raw hex (`MKR` → `4d4b52…00`). When the
 * decoded string is exactly such a hex-encoded `bytes32`, decode it back to UTF-8 and trim the zero
 * padding (issue #4873). Returns null on malformed input.
 */
internal fun decodeErc20MetadataString(raw: String): String? {
    return try {
        val bytes = raw.removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val length = BigInteger(bytes.sliceArray(32..63)).toInt()
        String(bytes.sliceArray(64 until 64 + length)).decodeBytes32HexOrSelf()
    } catch (_: NumberFormatException) {
        // Non-hex characters in the eth_call result, or an empty byte range fed to BigInteger.
        null
    } catch (_: IndexOutOfBoundsException) {
        // Result shorter than the ABI offset/length/data words require.
        null
    }
}

/**
 * Decodes a value that is the 64-char hex of a `bytes32` (right-padded with zeros) back to UTF-8
 * text, trimming the zero padding. Returns the receiver unchanged when it is not such a value — so
 * it is safe to apply to any name/symbol string (a normal ticker like `MKR` passes straight
 * through). Used both for ABI `eth_call` results ([decodeErc20MetadataString]) and for aggregator
 * token metadata that already arrives as the bare `bytes32` hex (issue #4873).
 */
internal fun String.decodeBytes32HexOrSelf(): String {
    if (length != 64 || any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return this
    val raw = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val text = raw.takeWhile { it.toInt() != 0 }.toByteArray()
    if (text.isEmpty()) return this
    val decoded = String(text, Charsets.UTF_8)
    // Only treat it as bytes32 text when the result is printable ASCII; otherwise the 64-char hex
    // was a genuine (if unusual) symbol, so leave it untouched.
    return if (decoded.all { it.code in 0x20..0x7E }) decoded else this
}
