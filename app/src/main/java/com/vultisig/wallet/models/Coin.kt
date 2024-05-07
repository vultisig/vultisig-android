package com.vultisig.wallet.models

import android.os.Parcelable
import com.vultisig.wallet.R
import com.vultisig.wallet.common.SettingsCurrency
import kotlinx.parcelize.Parcelize
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Parcelize
data class Coin(
    val chain: Chain,
    val ticker: String,
    val logo: String,
    val address: String,
    val decimal: Int,
    val hexPublicKey: String,
    val feeUnit: String,
    val feeDefault: BigDecimal,
    val priceProviderID: String,
    val contractAddress: String,
    val rawBalance: BigInteger,
    val isNativeToken: Boolean,
    val priceRate: BigDecimal,
    val currency: SettingsCurrency = SettingsCurrency.USD,
) : Parcelable {
    val coinType: CoinType
        get() = when (chain) {
            Chain.bitcoin -> CoinType.BITCOIN
            Chain.bitcoinCash -> CoinType.BITCOINCASH
            Chain.litecoin -> CoinType.LITECOIN
            Chain.dogecoin -> CoinType.DOGECOIN
            Chain.dash -> CoinType.DASH
            Chain.thorChain -> CoinType.THORCHAIN
            Chain.mayaChain -> CoinType.THORCHAIN
            Chain.ethereum -> CoinType.ETHEREUM
            Chain.solana -> CoinType.SOLANA
            Chain.avalanche -> CoinType.AVALANCHECCHAIN
            Chain.base -> CoinType.BASE
            Chain.blast -> CoinType.BLAST
            Chain.arbitrum -> CoinType.ARBITRUM
            Chain.polygon -> CoinType.POLYGON
            Chain.optimism -> CoinType.OPTIMISM
            Chain.bscChain -> CoinType.SMARTCHAIN
            Chain.gaiaChain -> CoinType.COSMOS
            Chain.kujira -> CoinType.KUJIRA
            Chain.cronosChain -> CoinType.CRONOSCHAIN
        }

}

fun Coin.getBalance(): BigDecimal {
    return BigDecimal(rawBalance)
        .divide(BigDecimal(10).pow(decimal))
        .setScale(2, RoundingMode.HALF_UP)
}

fun Coin.getBalanceInFiat(): BigDecimal {
    return getBalance()
        .multiply(priceRate)
        .setScale(2, RoundingMode.HALF_UP)
}

fun Coin.getBalanceInFiatString(): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    format.currency = Currency.getInstance(currency.name)
    return format.format(getBalanceInFiat())
}

object Coins {
    val SupportedCoins = listOf(
        Coin(
            chain = Chain.bitcoin,
            ticker = "BTC",
            logo = "btc",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "Sats/vbytes",
            feeDefault = BigDecimal(20),
            priceProviderID = "bitcoin",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.bitcoinCash,
            ticker = "BCH",
            logo = "bch",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "Sats/vbytes",
            feeDefault = BigDecimal(20),
            priceProviderID = "bitcoin-cash",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.litecoin,
            ticker = "LTC",
            logo = "ltc",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "Lits/vbytes",
            feeDefault = BigDecimal(1000),
            priceProviderID = "litecoin",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.dogecoin,
            ticker = "DOGE",
            logo = "doge",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "Doge/vbytes",
            feeDefault = BigDecimal(1000000),
            priceProviderID = "dogecoin",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.dash,
            ticker = "DASH",
            logo = "dash",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "Sats/vbytes",
            feeDefault = BigDecimal(20),
            priceProviderID = "dash",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.thorChain,
            ticker = "RUNE",
            logo = "rune",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "Rune",
            feeDefault = BigDecimal(0.02),
            priceProviderID = "thorchain",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.mayaChain,
            ticker = "CACAO",
            logo = "cacao",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "cacao",
            feeDefault = BigDecimal(0.02),
            priceProviderID = "cacao",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.mayaChain,
            ticker = "MAYA",
            logo = "maya",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            feeUnit = "cacao",
            feeDefault = BigDecimal(0.02),
            priceProviderID = "maya",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            feeUnit = "Gwei",
            feeDefault = BigDecimal(23000),
            priceProviderID = "ethereum",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
        Coin(
            chain = Chain.solana,
            ticker = "SOL",
            logo = "sol",
            address = "",
            decimal = 9,
            hexPublicKey = "",
            feeUnit = "Lamports",
            feeDefault = BigDecimal(7000),
            priceProviderID = "solana",
            contractAddress = "",
            rawBalance = BigInteger.ZERO,
            isNativeToken = true,
            priceRate = BigDecimal.ZERO
        ),
    )

    fun getCoin(ticker: String, address: String, hexPublicKey: String, coinType: CoinType): Coin? {
        return SupportedCoins.find { it.ticker == ticker && it.coinType == coinType }
            ?.copy(address = address, hexPublicKey = hexPublicKey)

    }

    fun getCoinLogo(logoName: String): Int {
        return when (logoName) {
            "btc" -> R.drawable.bitcoin
            "bch" -> R.drawable.bitcoincash
            "ltc" -> R.drawable.litecoin
            "doge" -> R.drawable.doge
            "dash" -> R.drawable.dash
            "rune" -> R.drawable.rune
            "eth" -> R.drawable.ethereum
            "sol" -> R.drawable.solana
            "cacao" -> R.drawable.danger
            "maya" -> R.drawable.danger
            else -> R.drawable.danger
        }
    }
}