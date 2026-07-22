package com.vultisig.wallet.ui.models.solanastaking

import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingService
import com.vultisig.wallet.data.blockchain.solana.staking.ValidatorMetadataProvider
import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject

/**
 * Loads the non-delinquent Solana validators (sorted by activated stake, descending) and enriches
 * them with off-chain metadata into [SolanaValidatorOption]s for the delegate / move / finish-move
 * validator pickers. A metadata outage degrades to a truncated vote pubkey and no logo.
 */
internal class LoadSolanaValidatorOptionsUseCase
@Inject
constructor(
    private val solanaStakingService: SolanaStakingService,
    private val validatorMetadataProvider: ValidatorMetadataProvider,
) {
    private val stakeFormat = DecimalFormat("#,###")

    suspend operator fun invoke(coin: Coin): List<SolanaValidatorOption> {
        val validators =
            solanaStakingService
                .fetchValidators()
                .filter { !it.delinquent }
                .sortedByDescending { it.activatedStake }
        val metadata = validatorMetadataProvider.metadata(validators.map { it.votePubkey })
        return validators.map { v ->
            val md = metadata[v.votePubkey]
            SolanaValidatorOption(
                votePubkey = v.votePubkey,
                name = md?.name?.takeIf { it.isNotBlank() } ?: shortAddress(v.votePubkey),
                logoUrl = md?.logoUrl,
                activatedStakeDisplay =
                    "${stakeFormat.format(v.activatedStake.toBigDecimal().movePointLeft(coin.decimal).toBigInteger())} ${coin.ticker}",
                commissionDisplay = "${v.commission}%",
                apyDisplay =
                    md?.apyEstimate?.let {
                        it.multiply(BigDecimal(100))
                            .setScale(2, RoundingMode.HALF_UP)
                            .toPlainString() + "%"
                    },
            )
        }
    }

    private fun shortAddress(address: String): String =
        if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address
}
