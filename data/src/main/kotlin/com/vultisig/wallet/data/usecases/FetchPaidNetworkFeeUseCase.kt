package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.paidFeeNanoton
import com.vultisig.wallet.data.api.models.paidFeeWei
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import java.math.BigInteger
import javax.inject.Inject

/**
 * Reads the network fee a settled transaction actually paid, in the chain's native base unit, so the
 * done screen and History can replace the pre-sign estimate with it. Only [txHash]'s own fee is
 * read: an approval broadcast before it is a separate transaction.
 */
interface FetchPaidNetworkFeeUseCase {

    /** True for chains whose paid fee can be read back once the transaction settles. */
    fun supports(chain: Chain): Boolean

    /**
     * Returns the fee [txHash] paid on [chain], or null when [chain] isn't supported or the fee
     * isn't known yet: no receipt / indexed transaction, or one missing the fee fields.
     */
    suspend operator fun invoke(chain: Chain, txHash: String): BigInteger?
}

class FetchPaidNetworkFeeUseCaseImpl
@Inject
constructor(private val evmApiFactory: EvmApiFactory, private val tonApi: TonApi) :
    FetchPaidNetworkFeeUseCase {

    override fun supports(chain: Chain): Boolean =
        chain.standard == TokenStandard.EVM || chain == Chain.Ton

    override suspend fun invoke(chain: Chain, txHash: String): BigInteger? =
        when {
            chain.standard == TokenStandard.EVM ->
                evmApiFactory.createEvmApi(chain).getTxStatus(txHash)?.result?.paidFeeWei()
            // The broadcast hash is the external message's hash, the same key the status poll
            // reads. `total_fees` is the wallet transaction's own charge, which is what tonviewer
            // and the extension show; TON the wallet attaches for a contract's forward gas is part
            // of the sent value, not this fee.
            chain == Chain.Ton -> tonApi.getTsStatus(txHash).paidFeeNanoton()
            else -> null
        }
}
