package com.vultisig.wallet.data.usecases

import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

private const val GWEI_IN_WEI = 1_000_000_000

interface ConvertWeiToGweiUseCase : (BigInteger) -> BigDecimal

internal class ConvertWeiToGweiUseCaseImpl @Inject constructor() : ConvertWeiToGweiUseCase {
    override fun invoke(wei: BigInteger): BigDecimal =
        wei.toBigDecimal().divide(GWEI_IN_WEI.toBigDecimal())

}

interface ConvertGweiToWeiUseCase : (BigDecimal) -> BigDecimal

internal class ConvertGweiToWeiUseCaseImpl @Inject constructor() : ConvertGweiToWeiUseCase {
    override fun invoke(gwei: BigDecimal): BigDecimal =
        gwei.multiply(GWEI_IN_WEI.toBigDecimal())
}