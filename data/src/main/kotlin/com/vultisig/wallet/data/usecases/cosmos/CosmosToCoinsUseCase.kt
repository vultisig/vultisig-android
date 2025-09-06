package com.vultisig.wallet.data.usecases.cosmos

import com.vultisig.wallet.data.api.models.cosmos.CosmosBankToken
import com.vultisig.wallet.data.api.models.cosmos.CosmosTokenMetadata
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import jakarta.inject.Inject
import timber.log.Timber
import java.math.BigInteger

interface CosmosToCoinsUseCase : (List<CosmosBankToken>, Chain) -> List<Coin>

 class CosmosToCoinsUseCaseImpl @Inject constructor() : CosmosToCoinsUseCase {
    
    override fun invoke(tokens: List<CosmosBankToken>, chain: Chain): List<Coin> {
        return tokens.mapNotNull { token ->
            token.toCoin(chain)
        }
    }
    
    private fun CosmosBankToken.toCoin(chain: Chain): Coin? {
        // Skip native chain tokens (already in built-in tokens)
        if (isNativeToken(denom, chain)) return null
        
        // Skip tokens with zero/minimal supply
        val supply = amount.toBigIntegerOrNull() ?: return null
        if (supply <= BigInteger.valueOf(1000)) return null
        
        val metadata = this.metadata
        val ticker = deriveTicker(denom, metadata)
        
        // Skip if we can't determine decimals
        val decimals = metadata?.decimals ?: getDefaultDecimals(denom)
        if (decimals == null) {
            Timber.d("Skipping token $denom - no decimals info")
            return null
        }
        
        val name = metadata?.name?.takeIf { it.isNotBlank() } ?: ticker
        
        return Coin(
            chain = chain,
            ticker = ticker,
            logo = generateLogoUrl(chain, denom, ticker),
            decimal = decimals,
            priceProviderID = generatePriceProviderId(denom, ticker),
            contractAddress = denom,
            isNativeToken = false,
            address = "", // Will be set by caller
            hexPublicKey = "" // Will be set by caller
        )
    }
    
    private fun deriveTicker(denom: String, metadata: CosmosTokenMetadata?): String {
        // Follow Windows logic exactly
        if (metadata?.symbol?.isNotEmpty() == true) return metadata.symbol
        if (metadata?.display?.isNotEmpty() == true) return metadata.display
        
        return when {
            denom.startsWith("x/staking-") -> {
                val base = denom.removePrefix("x/staking-")
                "S$base"
            }
            denom.startsWith("x/") -> {
                denom.split("/").lastOrNull() ?: denom
            }
            denom.startsWith("factory/") -> {
                val sub = denom.split("/").lastOrNull() ?: denom
                sub.removePrefix("u")
            }
            denom.startsWith("ibc/") -> {
                "IBC-${denom.takeLast(6).uppercase()}"
            }
            denom.startsWith("u") && denom.length > 1 -> {
                denom.drop(1).uppercase()
            }
            denom.startsWith("a") && denom.length > 1 -> {
                denom.drop(1).uppercase()
            }
            else -> denom.uppercase()
        }
    }
    
    private fun getDefaultDecimals(denom: String): Int? {
        return when {
            denom.startsWith("u") -> 6
            denom.startsWith("a") -> 18
            denom.startsWith("ibc/") -> 6
            denom.startsWith("factory/") -> 6
            else -> null // Don't assume for unknown formats
        }
    }
    
    private fun isNativeToken(denom: String, chain: Chain): Boolean {
        return when (chain) {
            Chain.GaiaChain -> denom == "uatom"
            Chain.Osmosis -> denom == "uosmo"
            Chain.ThorChain -> denom == "rune"
            Chain.Kujira -> denom == "ukuji"
            Chain.Dydx -> denom == "adydx"
            Chain.Terra -> denom == "uluna"
            Chain.TerraClassic -> denom == "uluna"
            Chain.Noble -> denom == "uusdc"
            Chain.Akash -> denom == "uakt"
            Chain.MayaChain -> denom == "cacao"
            else -> false
        }
    }
    
    private fun generateLogoUrl(chain: Chain, denom: String, ticker: String): String {
        return when {
            ticker.contains("USDC", ignoreCase = true) -> "usdc"
            ticker.contains("USDT", ignoreCase = true) -> "usdt"
            ticker.contains("ATOM", ignoreCase = true) -> "atom"
            ticker.contains("OSMO", ignoreCase = true) -> "osmo"
            denom.startsWith("ibc/") -> "usdc" // Default for IBC tokens
            else -> ticker.lowercase()
        }
    }
    
    private fun generatePriceProviderId(denom: String, ticker: String): String {
        return when {
            ticker.contains("USDC", ignoreCase = true) -> "usd-coin"
            ticker.contains("USDT", ignoreCase = true) -> "tether"
            ticker.contains("ATOM", ignoreCase = true) -> "cosmos"
            ticker.contains("OSMO", ignoreCase = true) -> "osmosis"
            else -> ""
        }
    }
}