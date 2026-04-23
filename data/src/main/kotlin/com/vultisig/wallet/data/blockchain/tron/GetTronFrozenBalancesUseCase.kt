package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SUN_PER_TRX = 1_000_000L
private const val TRX_SCALE = 6

data class TronFrozenBalances(val bandwidthTrx: BigDecimal, val energyTrx: BigDecimal)

class GetTronFrozenBalancesUseCase @Inject constructor(private val tronApi: TronApi) {

    suspend operator fun invoke(address: String): TronFrozenBalances {
        val account = withContext(Dispatchers.IO) { tronApi.getAccount(address) }
        return TronFrozenBalances(
            bandwidthTrx = account.frozenBandwidthSun.sunToTrx(),
            energyTrx = account.frozenEnergySun.sunToTrx(),
        )
    }

    private fun Long.sunToTrx(): BigDecimal =
        BigDecimal(this).divide(BigDecimal(SUN_PER_TRX), TRX_SCALE, RoundingMode.DOWN)
}
