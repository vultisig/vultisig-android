package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.models.VultisigBalanceResultJson
import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.Vault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.collections.first
import kotlin.collections.map

interface TokenRepository {

    suspend fun getToken(tokenId: String): Coin?

    fun getChainTokens(chain: Chain, vault: Vault): Flow<List<Coin>>

    suspend fun getNativeToken(chainId: String): Coin

    suspend fun getTokenByContract(chainId: String, contractAddress: String): Coin?

    suspend fun getTokensWithBalance(chain: Chain, address: String): List<Coin>

    suspend fun getRefreshTokens(chain: Chain, vault: Vault): List<Coin>

    val builtInTokens: Flow<List<Coin>>

    val nativeTokens: Flow<List<Coin>>

}

internal class TokenRepositoryImpl @Inject constructor(
    private val oneInchApi: OneInchApi,
    private val evmApiFactory: EvmApiFactory,
    private val thorApi: ThorChainApi,
    private val splTokenRepository: SplTokenRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val currencyRepository: AppCurrencyRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : TokenRepository {

    override suspend fun getToken(tokenId: String): Coin? =
        builtInTokens.map { allTokens -> allTokens.firstOrNull { it.id == tokenId } }.firstOrNull()

    override fun getChainTokens(
        chain: Chain,
        vault: Vault,
    ): Flow<List<Coin>> = flow {

        val builtInTokens = builtInTokens.first().filter { it.chain == chain }
        emitUniqueTokens(builtInTokens)

        val refreshedTokens = getRefreshTokens(chain, vault)
        emitUniqueTokens(
            builtInTokens,
            refreshedTokens
        )

        when (chain.standard) {
            TokenStandard.EVM -> {
                emitEvmTokens(chain, refreshedTokens, builtInTokens)
            }
            TokenStandard.SOL -> {
                emitSolTokens(vault, chain, refreshedTokens, builtInTokens)
            }
            else -> Unit
        }
    }

    private suspend fun FlowCollector<List<Coin>>.emitEvmTokens(
        chain: Chain,
        refreshedTokens: List<Coin>,
        builtInTokens: List<Coin>,
    ) {
        val oneInchTokens = oneInchApi.getTokens(chain)
        emitUniqueTokens(
            oneInchTokens.tokens.toCoins(chain),
            refreshedTokens,
            builtInTokens,
        )
    }

    private suspend fun FlowCollector<List<Coin>>.emitSolTokens(
        vault: Vault,
        chain: Chain,
        refreshedTokens: List<Coin>,
        builtInTokens: List<Coin>,
    ) {
        val address = vault.coins.first { it.chain == chain }.address
        val tokens = splTokenRepository.getTokens(address)
        emitUniqueTokens(
            refreshedTokens,
            builtInTokens,
            tokens,
        )

        val jupiterTokens = splTokenRepository.getJupiterTokens()
        emitUniqueTokens(
            refreshedTokens,
            builtInTokens,
            tokens,
            jupiterTokens
        )
    }

    private suspend fun FlowCollector<List<Coin>>.emitUniqueTokens(vararg items: List<Coin>) {
        val coins = items.toList()
            .flatten()
            .asSequence()
            .distinctBy { it.contractAddress to it.chain.id }
            .toList()
        emit(coins)
    }

    override suspend fun getNativeToken(chainId: String): Coin =
        nativeTokens.map { it -> it.first { it.chain.id == chainId } }.first()

    override suspend fun getTokenByContract(chainId: String, contractAddress: String): Coin? {
        val chain = Chain.fromRaw(chainId)
        val rpcResponses = evmApiFactory.createEvmApi(chain)
            .findCustomToken(contractAddress)
        if (rpcResponses.isEmpty())
            return null
        var ticker = ""
        var decimal = 0
        rpcResponses.forEach {
            val result = it.result ?: return null
            if (it.id == CUSTOM_TOKEN_RESPONSE_TICKER_ID)
                ticker = result.decodeContractString() ?: return null
            else decimal =
                result.decodeContractDecimal().takeIf { dec -> dec != 0 } ?: return null
        }
        val coin = Coin(
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

    override suspend fun getTokensWithBalance(chain: Chain, address: String): List<Coin> {
        return when (chain) {
            Chain.ThorChain -> {
                thorApi.getBalance(address)
                    .mapNotNull {
                        val denom = it.denom
                        var symbol = ""

                        if (denom.contains(".")) {
                            val parts = denom.split(".")
                            if (parts.size >= 2) {
                                symbol = parts[1].uppercase()
                            }
                        } else if (denom.startsWith("x/",true)) {
                            val parts = denom.split("/")
                            if (parts.size >= 2) {
                                symbol = parts[1].uppercase()
                            }
                        }
                        else if (denom.contains("-")) {
                            val parts = denom.split("-")
                            if (parts.size >= 2) {
                                symbol = parts[1].uppercase()
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
                                decimal = 8,
                                isNativeToken = false,
                                priceProviderID = "",

                                address = "",
                                hexPublicKey = "",
                            )
                        }
                    }
            }
            Chain.BscChain, Chain.Avalanche,
            Chain.Ethereum, Chain.Arbitrum,
                -> {
                val evmApi = evmApiFactory.createEvmApi(chain)
                evmApi.getBalances(address).result.toCoins(chain)
            }
            else -> {
                // cant get this for non EVM chains right now
                if (chain.standard != TokenStandard.EVM) return emptyList()
                // 1inch api does not support cronos chain and blast chain
                if (chain in listOf(Chain.CronosChain, Chain.Blast)) return emptyList()

                Timber.d("getTokensWithBalance(chain = $chain, address = $address)")
                val contractsWithBalance = oneInchApi.getContractsWithBalance(chain, address)
                if (contractsWithBalance.isEmpty()) return emptyList()

                delay(1000) //TODO remove when we will use api without rate limit

                val oneInchTokensWithBalance =
                    oneInchApi.getTokensByContracts(chain, contractsWithBalance)
                return oneInchTokensWithBalance
                    .toCoins(chain)
            }
        }
    }

    override suspend fun getRefreshTokens(chain: Chain, vault: Vault): List<Coin> {
        val (address, derivedPublicKey) = chainAccountAddressRepository.getAddress(
            chain,
            vault
        )
        return (getTokensWithBalance(chain, address) + enabledByDefaultTokens.getOrDefault(
            chain,
            emptyList()
        ))
            .filterNot {
                it.isNativeToken
            }
            .map { token ->
                token.copy(
                    address = address,
                    hexPublicKey = derivedPublicKey
                )
            }
    }

    private suspend fun VultisigBalanceResultJson.toCoins(chain: Chain) = coroutineScope {
        val (supportedCoinsAndContracts, unsupportedCoins) = extractCoinsFromJson(
            this@toCoins,
            chain
        )
        val validContractAddress = cleanContractAddressList(
            unsupportedCoins.map { (contractAddress, _) -> contractAddress },
            chain,
        )
        val supportedCoins = supportedCoinsAndContracts
            .mapNotNull { (_, coin) -> coin }

        val newCoins = getNewCoins(validContractAddress, chain)
        supportedCoins + newCoins
    }

    private suspend fun getNewCoins(
        validContractAddress: List<String>,
        chain: Chain,
    ) = coroutineScope {
        validContractAddress.map { contractAddress ->
            async {
                getTokenByContract(chain.id, contractAddress)
            }
        }
    }.awaitAll().filterNotNull()

    private fun extractCoinsFromJson(response: VultisigBalanceResultJson, chain: Chain) =
        response.tokenBalances
            .filter {
                BigInteger(
                    it.balance.stripHexPrefix(),
                    16
                ) > BigInteger.ZERO
            }
            .map { json ->
                val supportedCoin =
                    Coins.coins.getOrDefault(chain, emptyList()).firstOrNull {
                        json.contractAddress.equals(
                            it.contractAddress,
                            true
                        )
                    }
                json.contractAddress to supportedCoin?.let {
                    createCoin(
                        json.contractAddress,
                        supportedCoin
                    )
                }
            }.partition { (_, supportedCoin) ->
                supportedCoin != null
            }

    private suspend fun cleanContractAddressList(
        unsupportedContracts: List<String>,
        chain: Chain,
    ) = coroutineScope {
        coinGeckoApi.getContractsPrice(
            chain = chain,
            contractAddresses = unsupportedContracts,
            currencies = listOf(currencyRepository.currency.first().ticker),
        ).keys.toList()
    }

    private fun createCoin(contractAddress: String, supportedCoin: Coin) = Coin(
        contractAddress = contractAddress,
        chain = supportedCoin.chain,
        ticker = supportedCoin.ticker,
        logo = supportedCoin.logo,
        decimal = supportedCoin.decimal,
        isNativeToken = supportedCoin.isNativeToken,
        priceProviderID = supportedCoin.priceProviderID,
        address = "",
        hexPublicKey = "",
    )

    override val builtInTokens: Flow<List<Coin>> = flowOf(Coins.coins.flatMap { it.value })

    override val nativeTokens: Flow<List<Coin>> = builtInTokens
        .map { it.filterNatives() }

    private fun Iterable<Coin>.filterNatives() =
        filter { it.isNativeToken }

    private fun String.decodeContractString(): String? {
        try {
            val bytes =
                removePrefix("0x")
                    .chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
            val length = BigInteger(bytes.sliceArray(32..63)).toInt()
            return String(bytes.sliceArray(64 until 64 + length)).lowercase()
        } catch (e: Exception) {
            return null
        }
    }

    private fun String.decodeContractDecimal(): Int {
        return BigInteger(
            removePrefix("0x"),
            16
        ).toInt()
    }

    private fun Map<String, OneInchTokenJson>.toCoins(chain: Chain): List<Coin> =
        asSequence()
            .map { it.value }
            .map {
                val supportedCoin = Coins.coins.getOrDefault(chain, emptyList())
                    .firstOrNull { coin -> coin.id == "${it.symbol}-${chain.id}" }
                Coin(
                    contractAddress = it.address,
                    chain = chain,
                    ticker = it.symbol,
                    logo = it.logoURI ?: "",
                    decimal = it.decimals,
                    isNativeToken = supportedCoin?.isNativeToken == true,
                    priceProviderID = "",
                    address = "",
                    hexPublicKey = "",
                )
            }
            .toList()


    private val enabledByDefaultTokens = listOf(Tokens.tcy)
        .groupBy { it.chain }


    companion object {
        private const val CUSTOM_TOKEN_RESPONSE_TICKER_ID = 2
    }

}
