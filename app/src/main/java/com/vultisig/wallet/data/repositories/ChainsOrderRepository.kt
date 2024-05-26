package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.ChainOrderDao
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject


internal interface ChainsOrderRepository {
    fun loadByOrders(): Flow<List<ChainOrderEntity>>
    suspend fun updateItemOrder(lowerVal: String?, middleVal: String, upperVal: String?)
    suspend fun delete(name: String)
    suspend fun find(name: String): ChainOrderEntity
    suspend fun insert(name: String)
    suspend fun findMaxOrder(): ChainOrderEntity?
}


internal class ChainsOrderRepositoryImpl @Inject constructor(
    private val chainOrderDao: ChainOrderDao
) : ChainsOrderRepository {
    override fun loadByOrders(): Flow<List<ChainOrderEntity>> {
        return chainOrderDao.loadByOrders()
    }

    override suspend fun updateItemOrder(lowerVal: String?, middleVal: String, upperVal: String?) {
        withContext(IO) {
            val lowerChainEntity = if (lowerVal != null)
                chainOrderDao.find(lowerVal)
            else ChainOrderEntity(trueFraction = "0/1", fractionInFloat = 0f)
            val middleChainEntity = chainOrderDao.find(middleVal)
            val upperChainEntity = if (upperVal != null) chainOrderDao.find(upperVal)
            else {
                val maxOrder = findMaxOrder() ?: ChainOrderEntity(
                    "", "0/1", 0f
                )
                val maxOrderFraction = maxOrder.trueFraction.extractFraction()
                ChainOrderEntity(
                    trueFraction = (
                            maxOrderFraction.first + maxOrderFraction.second
                                    to maxOrderFraction.second)
                        .toFractionString(),
                    fractionInFloat = maxOrder.fractionInFloat + 1
                )
            }
            val (lowerNumerator, lowerDenominator) =
                lowerChainEntity.trueFraction.extractFraction()
            val (upperNumerator, upperDenominator) =
                upperChainEntity.trueFraction.extractFraction()
            val newMiddleTrueFractionPair =
                (lowerNumerator + upperNumerator) to (lowerDenominator + upperDenominator)
            val newMiddleFloatFraction: Float = newMiddleTrueFractionPair.first.toFloat()
                .div(newMiddleTrueFractionPair.second.toFloat())
            chainOrderDao.updateItemOrder(
                middleChainEntity.copy(
                    trueFraction = newMiddleTrueFractionPair.toFractionString(),
                    fractionInFloat = newMiddleFloatFraction
                )
            )
        }
    }

    override suspend fun delete(name: String) {
        withContext(IO) {
            chainOrderDao.delete(name)
        }
    }

    override suspend fun find(name: String): ChainOrderEntity {
        return withContext(IO) {
            chainOrderDao.find(name)
        }
    }

    override suspend fun findMaxOrder(): ChainOrderEntity? =
        withContext(IO) { chainOrderDao.getMaxChainOrder() }

    override suspend fun insert(name: String) {
        withContext(IO) {
            val firstChainOrder = ChainOrderEntity(
                value = "",
                trueFraction = "0/1",
                fractionInFloat = 0f
            )
            val maxOrder = findMaxOrder() ?: firstChainOrder
            val (numerator, denominator) = maxOrder.trueFraction.extractFraction()
            val newFloatFraction = maxOrder.fractionInFloat + 1
            val newTrueFraction = numerator + denominator to denominator
            val order = ChainOrderEntity(
                name,
                fractionInFloat = newFloatFraction,
                trueFraction = newTrueFraction.toFractionString()
            )
            chainOrderDao.insert(order)
        }
    }


    private fun String.extractFraction(): Pair<Int, Int> {
        val parts = this.split("/").map { it.toInt() }
        return Pair(parts[0], parts[1])
    }

    private fun Pair<Int, Int>.toFractionString(): String = "$first/$second"
}