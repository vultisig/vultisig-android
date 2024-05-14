package com.vultisig.wallet.data.models

import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import java.math.BigDecimal

internal data class Account(
    val token: Coin,
    val chainName: String,
    val logo: Int,
    val address: String,
    /**
    amount of native token for this chain on the address,
    null if unknown yet
     */
    val tokenValue: TokenValue?,
    /**
    amount of token for this chain on the address in fiat,
    null if unknown yet
     */
    val fiatValue: FiatValue?,
){
    val blockExplorerUrl: String
        get() {
            return when(token.chain){
                Chain.thorChain-> "https://runescan.io/address/$address"
                Chain.arbitrum -> "https://arbiscan.io/address/$address"
                Chain.avalanche -> "https://snowtrace.io/address/$address"
                Chain.base -> "https://basescan.org/address/$address"
                Chain.cronosChain -> "https://cronoscan.com/address/$address"
                Chain.bscChain -> "https://bscscan.com/address/$address"
                Chain.blast -> "https://blastscan.io/address/$address"
                Chain.ethereum -> "https://etherscan.io/address/$address"
                Chain.optimism -> "https://optimistic.etherscan.io/address/$address"
                Chain.polygon -> "https://polygonscan.com/address/$address"
                Chain.bitcoin -> "https://blockchair.com/bitcoin/address/$address"
                Chain.bitcoinCash -> "https://blockchair.com/bitcoin-cash/address/$address"
                Chain.litecoin -> "https://blockchair.com/litecoin/address/$address"
                Chain.dogecoin -> "https://blockchair.com/dogecoin/address/$address"
                Chain.dash -> "https://blockchair.com/dash/address/$address"
                Chain.solana -> "https://explorer.solana.com/address/$address"
                Chain.gaiaChain -> "https://www.mintscan.io/cosmos/address/$address"
                Chain.kujira -> "https://kujira.mintscan.io/address/$address"
                Chain.mayaChain -> "https://www.mayascan.org/address/$address"
            }
        }
}

internal fun List<Account>.calculateTotalFiatValue(): FiatValue? =
    this.fold(FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)) { acc, account ->
        // if any account dont have fiat value, return null, as in "not loaded yet"
        val fiatValue = account.fiatValue ?: return@calculateTotalFiatValue null
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }