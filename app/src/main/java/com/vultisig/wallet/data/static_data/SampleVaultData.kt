package com.vultisig.wallet.data.static_data

import com.vultisig.wallet.R
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import java.math.BigDecimal
import java.math.BigInteger
import com.vultisig.wallet.data.on_board.db.VaultDB

internal fun insertSampleVaultData(vaultDB: VaultDB)     {
    val vault= Vault("Main Vault",)
    vault.coins= SupportedCoins1;
    vaultDB.upsert(vault)
    //------------------------------------------------------------
    val vault2= Vault("Savings Vault",)
    vault2.coins= SupportedCoins2;
    vaultDB.upsert(vault2)
    //------------------------------------------------------------
    val vault3= Vault("Ethereum Vault",)
    vault3.coins= SupportedCoins3;
    vaultDB.upsert(vault3)
}

//-----------------------------------------------------------------------------

internal fun getSampleVaultData(): MutableList<Vault> {
    val items= mutableListOf<Vault>();
    val vault= Vault("Main Vault",)
    vault.coins= SupportedCoins1;
    items.add(vault)
    //------------------------------------------------------------
    val vault2= Vault("Savings Vault",)
    vault2.coins= SupportedCoins2;
    items.add(vault)
    //------------------------------------------------------------
    val vault3= Vault("Ethereum Vault",)
    vault3.coins= SupportedCoins3;
    items.add(vault)
    return items;
}

//-----------------------------------------------------------------------------

val SupportedCoins1 = mutableListOf(
    Coin(
        chain = Chain.bitcoin,
        ticker = "BTC",
        logo = R.drawable.crypto_bitcoin.toString()  ,
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 8,
        hexPublicKey = "",
        feeUnit = "Sats/vbytes",
        feeDefault = BigDecimal(20),
        priceProviderID = "bitcoin",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),
    Coin(
        chain = Chain.bitcoinCash,
        ticker = "BCH",
        logo = R.drawable.crypto_bitcoin.toString(),
        address = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
        decimal = 5,
        hexPublicKey = "",
        feeUnit = "Sats/vbytes",
        feeDefault = BigDecimal(20),
        priceProviderID = "bitcoin-cash",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(17),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(5.5)
    ),
    Coin(
        chain = Chain.litecoin,
        ticker = "LTC",
        logo = R.drawable.crypto_bitcoin.toString(),
        address ="bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
        decimal = 3,
        hexPublicKey = "",
        feeUnit = "Lits/vbytes",
        feeDefault = BigDecimal(1000),
        priceProviderID = "litecoin",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(5.5)
    ),
    Coin(
        chain = Chain.bitcoin,
        ticker = "BTC",
        logo = R.drawable.crypto_bitcoin.toString()  ,
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 8,
        hexPublicKey = "",
        feeUnit = "Sats/vbytes",
        feeDefault = BigDecimal(20),
        priceProviderID = "bitcoin",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(8),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(7.5)
    ),
    Coin(
        chain = Chain.dash,
        ticker = "DASH",
        logo = R.drawable.crypto_thorchain.toString(),
        address = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
        decimal = 3,
        hexPublicKey = "",
        feeUnit = "Sats/vbytes",
        feeDefault = BigDecimal(20),
        priceProviderID = "dash",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(13),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(5.5)
    ),
    Coin(
        chain = Chain.dash,
        ticker = "DASH",
        logo = R.drawable.crypto_thorchain.toString(),
        address = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
        decimal = 3,
        hexPublicKey = "",
        feeUnit = "Sats/vbytes",
        feeDefault = BigDecimal(20),
        priceProviderID = "dash",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(5.5)
    ),
    Coin(
        chain = Chain.ethereum,
        ticker = "ETH",
        logo =  R.drawable.crypto_ethereum.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 18,
        hexPublicKey = "",
        feeUnit = "Gwei",
        feeDefault = BigDecimal(23000),
        priceProviderID = "ethereum",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(14),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),
    Coin(
        chain = Chain.solana,
        ticker = "SOL",
        logo = R.drawable.crypto_solana.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 9,
        hexPublicKey = "",
        feeUnit = "Lamports",
        feeDefault = BigDecimal(7000),
        priceProviderID = "solana",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(8),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),
)
val SupportedCoins2 = mutableListOf(
    Coin(
        chain = Chain.bitcoin,
        ticker = "BTC",
        logo = R.drawable.crypto_bitcoin.toString()  ,
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 8,
        hexPublicKey = "",
        feeUnit = "Sats/vbytes",
        feeDefault = BigDecimal(20),
        priceProviderID = "bitcoin",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),

    Coin(
        chain = Chain.ethereum,
        ticker = "ETH",
        logo =  R.drawable.crypto_ethereum.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 3,
        hexPublicKey = "",
        feeUnit = "Gwei",
        feeDefault = BigDecimal(23000),
        priceProviderID = "ethereum",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(5),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(7.5)
    ),

    Coin(
        chain = Chain.thorChain,
        ticker = "RUNE",
        logo = R.drawable.crypto_thorchain.toString(),
        address = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
        decimal = 7,
        hexPublicKey = "",
        feeUnit = "Rune",
        feeDefault = BigDecimal(0.02),
        priceProviderID = "thorchain",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(3.5)
    ),
    Coin(
        chain = Chain.ethereum,
        ticker = "ETH",
        logo =  R.drawable.crypto_ethereum.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 10,
        hexPublicKey = "",
        feeUnit = "Gwei",
        feeDefault = BigDecimal(23000),
        priceProviderID = "ethereum",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(8),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(2.5)
    ),
    Coin(
        chain = Chain.mayaChain,
        ticker = "MAYA",
        logo = R.drawable.crypto_thorchain.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 8,
        hexPublicKey = "",
        feeUnit = "cacao",
        feeDefault = BigDecimal(0.02),
        priceProviderID = "maya",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.ZERO
    ),
    Coin(
        chain = Chain.ethereum,
        ticker = "ETH",
        logo =  R.drawable.crypto_ethereum.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 18,
        hexPublicKey = "",
        feeUnit = "Gwei",
        feeDefault = BigDecimal(23000),
        priceProviderID = "ethereum",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(14),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),
    Coin(
        chain = Chain.solana,
        ticker = "SOL",
        logo = R.drawable.crypto_solana.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 9,
        hexPublicKey = "",
        feeUnit = "Lamports",
        feeDefault = BigDecimal(7000),
        priceProviderID = "solana",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(8),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),
)
val SupportedCoins3 = mutableListOf(
    Coin(
        chain = Chain.ethereum,
        ticker = "ETH",
        logo =  R.drawable.crypto_ethereum.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 7,
        hexPublicKey = "",
        feeUnit = "Gwei",
        feeDefault = BigDecimal(23000),
        priceProviderID = "ethereum",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(15),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(9.5)
    ),

    Coin(
        chain = Chain.ethereum,
        ticker = "ETH",
        logo =  R.drawable.crypto_ethereum.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 3,
        hexPublicKey = "",
        feeUnit = "Gwei",
        feeDefault = BigDecimal(23000),
        priceProviderID = "ethereum",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(5),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(7.5)
    ),


    Coin(
        chain = Chain.mayaChain,
        ticker = "MAYA",
        logo = R.drawable.crypto_thorchain.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 8,
        hexPublicKey = "",
        feeUnit = "cacao",
        feeDefault = BigDecimal(0.02),
        priceProviderID = "maya",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(12),
        isNativeToken = true,
        priceRate = BigDecimal.ZERO
    ),
    Coin(
        chain = Chain.solana,
        ticker = "SOL",
        logo = R.drawable.crypto_solana.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 4,
        hexPublicKey = "",
        feeUnit = "Lamports",
        feeDefault = BigDecimal(7000),
        priceProviderID = "solana",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(11),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(8.5)
    ),
    Coin(
        chain = Chain.solana,
        ticker = "SOL",
        logo =R.drawable.crypto_solana.toString(),
        address = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2",
        decimal = 9,
        hexPublicKey = "",
        feeUnit = "Lamports",
        feeDefault = BigDecimal(7000),
        priceProviderID = "solana",
        contractAddress = "",
        rawBalance = BigInteger.valueOf(8),
        isNativeToken = true,
        priceRate = BigDecimal.valueOf(12.5)
    ),
)
//-----------------------------------------------------------------------------

