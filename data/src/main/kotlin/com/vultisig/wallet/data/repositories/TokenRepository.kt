package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.DenomMetadata
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainStakingContracts
import com.vultisig.wallet.data.chains.helpers.EthereumFunction
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.CardanoTokenFinder
import com.vultisig.wallet.data.usecases.CosmosBankCoinFinder
import com.vultisig.wallet.data.usecases.EvmCoinFinder
import com.vultisig.wallet.data.usecases.RippleTokenFinder
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
    private val rippleTokenFinder: RippleTokenFinder,
    private val cardanoTokenFinder: CardanoTokenFinder,
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
                ticker = EthereumFunction.symbolErc20Decoder(result) ?: return null
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
                    if (it.denom.lowercase() in DEFI_ONLY_THORCHAIN_DENOMS) return@mapNotNull null
                    // The enabledDenoms gate keeps the wallet from auto-adding arbitrary bank
                    // denoms the user never enabled. Curated liquid-bonding denoms are the
                    // exception: they must surface the first time a wallet holds one, before any
                    // manual enable, so they bypass the gate.
                    if (
                        enabledDenoms.isNotEmpty() &&
                            it.denom !in enabledDenoms &&
                            it.denom.lowercase() !in AUTO_DISCOVER_THORCHAIN_DENOMS
                    )
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
                    // Rujira's liquid-bonding denom. Restore its proper casing + curated logo, and
                    // store the canonical (lowercase) denom so case-sensitive consumers (isLpToken
                    // picker exclusion, contract-based pricing) stay on the right path. Only liquid
                    // bRUNE reaches here — the ybRUNE receipt is filtered out as DeFi-only above.
                    var contractAddress = it.denom
                    if (it.denom.lowercase() == Coins.ThorChain.bRUNE.contractAddress) {
                        symbol = Coins.ThorChain.bRUNE.ticker
                        contractAddress = Coins.ThorChain.bRUNE.contractAddress
                        decimal = Coins.ThorChain.bRUNE.decimal
                    }

                    if (denom == "rune") {
                        null
                    } else {
                        Coin(
                            contractAddress = contractAddress,
                            chain = chain,
                            ticker = symbol,
                            name = metadata?.name?.trim().orEmpty(),
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
            Chain.Ripple -> rippleTokenFinder.find(address)
            Chain.Cardano -> cardanoTokenFinder.find(address)
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
            .map { token ->
                Coins.withCuratedName(token)
                    .copy(address = address, hexPublicKey = derivedPublicKey)
            }
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

        private const val YRUNE_CONTRACT = ThorchainStakingContracts.YRUNE
        private const val YTCY_CONTRACT = ThorchainStakingContracts.YTCY
    }
}

// Denoms surfaced under the DeFi tab — must not be auto-discovered as wallet tokens. ybRUNE is the
// auto-compounding receipt for bonded bRUNE, so it belongs here alongside the sRUJI receipt rather
// than in the wallet's token list (iOS drops both via `defiOnlyTickers`).
// The on-chain sRUJI receipt denom is "x/staking-x/ruji"; the legacy "x/staking-ruji" spelling is
// kept defensively so the receipt stays excluded regardless of which the node reports.
// Stored lowercase — callers compare the lowercased denom, since node casing is not guaranteed.
internal val DEFI_ONLY_THORCHAIN_DENOMS =
    setOf("x/staking-ruji", "x/staking-x/ruji", Coins.ThorChain.ybRUNE.contractAddress)

// Curated Rujira liquid-bonding denoms that must auto-surface the first time a wallet receives one,
// before the user has manually enabled it. getRefreshTokens seeds enabledDenoms only from
// vault.coins, so a fresh holder (who only ever seeded native RUNE) would otherwise have these
// dropped by the enabledDenoms gate and never reach the canonicalization override. Stored lowercase
// to match the canonical denom casing the balance response is compared against.
// Liquid bRUNE only: its ybRUNE receipt is a DeFi position, dropped above before this gate.
internal val AUTO_DISCOVER_THORCHAIN_DENOMS = setOf(Coins.ThorChain.bRUNE.contractAddress)
