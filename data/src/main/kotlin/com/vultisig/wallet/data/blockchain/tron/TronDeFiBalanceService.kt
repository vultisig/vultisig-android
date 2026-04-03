package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger
import timber.log.Timber

class TronDeFiBalanceService(private val tronApi: TronApi) : DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> {
        return try {
            val account = tronApi.getAccount(address)
            val frozenBandwidth = account.frozenBandwidthSun.toBigInteger()
            val frozenEnergy = account.frozenEnergySun.toBigInteger()
            val unfreezing = account.unfreezingTotalSun.toBigInteger()
            val totalFrozen = frozenBandwidth + frozenEnergy + unfreezing

            Timber.d(
                "TronDeFiBalanceService: frozen bandwidth=%s, energy=%s, unfreezing=%s",
                frozenBandwidth,
                frozenEnergy,
                unfreezing,
            )

            if (totalFrozen == BigInteger.ZERO) {
                emptyList()
            } else {
                listOf(
                    DeFiBalance(
                        chain = Chain.Tron,
                        balances =
                            listOf(DeFiBalance.Balance(coin = Coins.Tron.TRX, amount = totalFrozen)),
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "TronDeFiBalanceService: Failed to fetch frozen TRX balance")
            emptyList()
        }
    }

    override suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance> =
        emptyList()
}
