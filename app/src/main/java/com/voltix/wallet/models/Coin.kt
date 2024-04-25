package com.voltix.wallet.models

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Objects

data class Coin (
    val chain: Chain,
    val ticker: String,
    val logo: String,
    var address: String,
    val decimal: Int,
    val HexPublicKey: String,
    val feeUnit: String,
    val feeDefault: BigDecimal,
    val priceProviderID: String,
    var contractAddress: String,
    var rawBalance: BigInteger,
    val isNativeToken: Boolean,
    var priceRate: BigDecimal,
)

object Coins {
    val SupportedCoins = listOf(
        Coin(chain = Chain.bitcoin, ticker = "BTC", logo = "btc", address = "", decimal = 8, HexPublicKey = "", feeUnit = "Sats/vbytes", feeDefault = BigDecimal(20), priceProviderID = "bitcoin", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.bitcoinCash, ticker = "BCH", logo = "bch", address = "", decimal = 8, HexPublicKey = "", feeUnit = "Sats/vbytes", feeDefault = BigDecimal(20), priceProviderID = "bitcoin-cash", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.litecoin, ticker = "LTC", logo = "ltc", address = "", decimal = 8, HexPublicKey = "", feeUnit = "Lits/vbytes", feeDefault = BigDecimal(1000), priceProviderID = "litecoin", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.dogecoin, ticker = "DOGE", logo = "doge", address = "", decimal = 8, HexPublicKey = "", feeUnit = "Doge/vbytes", feeDefault = BigDecimal(1000000), priceProviderID = "dogecoin", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.dash, ticker = "DASH", logo = "dash", address = "", decimal = 8, HexPublicKey = "", feeUnit = "Sats/vbytes", feeDefault = BigDecimal(20), priceProviderID = "dash", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.thorChain, ticker = "RUNE", logo = "rune", address = "", decimal = 8, HexPublicKey = "", feeUnit = "Rune", feeDefault = BigDecimal(0.02), priceProviderID = "thorchain", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.mayaChain, ticker = "CACAO", logo = "cacao", address = "", decimal = 8, HexPublicKey = "", feeUnit = "cacao", feeDefault = BigDecimal(0.02), priceProviderID = "cacao", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.mayaChain, ticker = "MAYA", logo = "maya", address = "", decimal = 8, HexPublicKey = "", feeUnit = "cacao", feeDefault = BigDecimal(0.02), priceProviderID = "maya", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.ethereum, ticker = "ETH", logo = "eth", address = "", decimal = 18, HexPublicKey = "", feeUnit = "Gwei", feeDefault = BigDecimal(23000), priceProviderID = "ethereum", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
        Coin(chain = Chain.solana, ticker = "SOL", logo = "sol", address = "", decimal = 9, HexPublicKey = "", feeUnit = "Lamports", feeDefault = BigDecimal(7000), priceProviderID = "solana", contractAddress = "", rawBalance = BigInteger.ZERO, isNativeToken = true, priceRate = BigDecimal.ZERO),
    )
}