package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import javax.inject.Inject

/*
fun interface ThorchainBondUseCase {
    suspend operator fun invoke(address: String)
}
 */

class ThorchainBondRepository@Inject constructor(
    private val thorChainApi: ThorChainApi,
) {

}