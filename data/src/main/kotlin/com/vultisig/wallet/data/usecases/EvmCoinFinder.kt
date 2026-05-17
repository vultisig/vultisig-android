package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.models.isCoinGeckoVerified
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.utils.NetworkException
import java.math.BigInteger
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * Auto-discovers ERC-20 holdings for an EVM wallet address.
 *
 * Ports the shared SDK's `findEvmCoins` resolver (`vultisig-sdk/.../find/resolvers/evm/index.ts`),
 * mirrored on iOS as `EvmCoinFinder.swift`. For 1inch-covered chains, holdings come from
 * `/balance/v1.2/{chain}/balances/{address}` (no Alchemy 100-token cap) and metadata from
 * `/token/v1.2/{chain}/custom`. Legitimacy is gated by the CoinGecko-provider allowlist, replacing
 * the empty-logo heuristic that was dropping legit small-caps (see #4555). For EVM chains outside
 * 1inch's surface — Blast / Cronos / Hyperliquid / Mantle / Sei / zkSync — discovery falls back to
 * `balanceOf`-iterating the curated [Coins] catalog.
 */
interface EvmCoinFinder {
    suspend fun find(chain: Chain, address: String): List<Coin>
}

internal class EvmCoinFinderImpl
@Inject
constructor(private val oneInchApi: OneInchApi, private val evmApiFactory: EvmApiFactory) :
    EvmCoinFinder {

    override suspend fun find(chain: Chain, address: String): List<Coin> {
        if (chain !in ONE_INCH_SUPPORTED_CHAINS) return findFallback(chain, address)

        val heldContracts = fetchHeldContracts(chain, address)
        val discovered =
            if (heldContracts.isEmpty()) emptyList()
            else fetchMetadataAndFilter(chain, heldContracts)

        return vultTopUpOnEthereum(chain, address, discovered)
    }

    private suspend fun fetchHeldContracts(chain: Chain, address: String): List<String> =
        try {
            oneInchApi
                .getContractsWithBalance(chain, address)
                .asSequence()
                .map { it.lowercase() }
                .filter { it != NATIVE_COIN_SENTINEL }
                .toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "1inch /balance failed for %s", chain.id)
            emptyList()
        }

    private suspend fun fetchMetadataAndFilter(chain: Chain, contracts: List<String>): List<Coin> {
        val info: Map<String, OneInchTokenJson> =
            try {
                oneInchApi.getTokensByContracts(chain, contracts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "1inch /token failed for %s", chain.id)
                return emptyList()
            }

        return contracts.mapNotNull { contract ->
            val token = info[contract] ?: return@mapNotNull null
            val logo = token.logoURI?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!token.isCoinGeckoVerified) return@mapNotNull null
            preferCurated(chain, contract) ?: fromOneInch(chain, contract, token, logo)
        }
    }

    private suspend fun findFallback(chain: Chain, address: String): List<Coin> {
        val curated =
            Coins.coins[chain]?.filter { !it.isNativeToken && it.contractAddress.isNotEmpty() }
                .orEmpty()
        if (curated.isEmpty()) return emptyList()

        val evmApi = evmApiFactory.createEvmApi(chain)
        return coroutineScope {
            curated
                .map { coin ->
                    async {
                        coin.takeIf {
                            balanceOrZero(evmApi, address, coin.contractAddress) > BigInteger.ZERO
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun vultTopUpOnEthereum(
        chain: Chain,
        address: String,
        discovered: List<Coin>,
    ): List<Coin> {
        if (chain != Chain.Ethereum) return discovered
        val alreadyHasVult =
            discovered.any { it.contractAddress.equals(VULT_ETHEREUM_CONTRACT, ignoreCase = true) }
        if (alreadyHasVult) return discovered

        val vult =
            Coins.coins[Chain.Ethereum]?.firstOrNull {
                it.contractAddress.equals(VULT_ETHEREUM_CONTRACT, ignoreCase = true)
            } ?: return discovered

        val balance =
            balanceOrZero(evmApiFactory.createEvmApi(chain), address, VULT_ETHEREUM_CONTRACT)
        return if (balance > BigInteger.ZERO) discovered + vult else discovered
    }

    private suspend fun balanceOrZero(
        evmApi: EvmApi,
        address: String,
        contractAddress: String,
    ): BigInteger =
        try {
            evmApi.getERC20Balance(address, contractAddress)
        } catch (e: SocketTimeoutException) {
            Timber.d(e, "ERC-20 balance timed out for %s", contractAddress)
            BigInteger.ZERO
        } catch (e: NetworkException) {
            Timber.d(
                "ERC-20 balance RPC error for %s: status=%d message=%s",
                contractAddress,
                e.httpStatusCode,
                e.message,
            )
            BigInteger.ZERO
        }

    private fun preferCurated(chain: Chain, contract: String): Coin? =
        Coins.coins[chain]?.firstOrNull { it.contractAddress.equals(contract, ignoreCase = true) }

    private fun fromOneInch(
        chain: Chain,
        contract: String,
        token: OneInchTokenJson,
        logo: String,
    ): Coin =
        Coin(
            chain = chain,
            ticker = token.symbol,
            logo = logo,
            address = "",
            decimal = token.decimals,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = false,
        )

    companion object {
        /** Chains 1inch covers with `/balance` + `/token` APIs; matches the SDK resolver. */
        val ONE_INCH_SUPPORTED_CHAINS: Set<Chain> =
            setOf(
                Chain.Ethereum,
                Chain.Base,
                Chain.Arbitrum,
                Chain.Polygon,
                Chain.Optimism,
                Chain.BscChain,
                Chain.Avalanche,
            )

        /** Placeholder 1inch uses for the chain's native gas token; already covered separately. */
        private const val NATIVE_COIN_SENTINEL = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

        /** VULT (Vultisig) ERC-20 on Ethereum; topped up directly when 1inch omits it. */
        private const val VULT_ETHEREUM_CONTRACT = "0xb788144df611029c60b859df47e79b7726c4deba"
    }
}
