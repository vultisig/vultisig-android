package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigInteger
import javax.inject.Inject
import timber.log.Timber

/**
 * Rejects an EVM payload whose signed gas price or gas limit is implausibly large, before anything
 * is signed.
 *
 * The co-signer builds the signing input from the payload's [BlockChainSpecific.Ethereum] fields
 * ([com.vultisig.wallet.data.chains.helpers.EthereumGasHelper] reads `maxFeePerGasWei` straight into
 * the signed `gasPrice` on BSC and into `maxFeePerGas` elsewhere), so a compromised initiator can
 * transmit an inflated fee and the co-signer would sign it. The Verify screen shows that fee, but a
 * secondary row is easy to miss and nothing stops an absurd value. The loss is worst on legacy-gas
 * chains (BSC): `gasPrice × gasUsed` is burned to the validator in full, with no base-fee refund.
 *
 * There is no single safe constant across chains and congestion, so the gas price is checked against
 * a fresh `eth_gasPrice` reference, which already rises with congestion, times a generous
 * [MAX_GAS_PRICE_MULTIPLE] — far above any manual fee a user would set, well below a ×1000 attack.
 * The gas limit is held under [MAX_GAS_LIMIT], which no single transaction can legitimately exceed
 * (above a whole block's gas). Only [BlockChainSpecific.Ethereum] on an EVM chain is checked;
 * everything else returns.
 *
 * Throws on a breach, and lets a failed reference fetch propagate: a fee that can't be checked is
 * not signed. Normal fees pass through untouched. Mirrors the Solana priority-fee ceiling (#6022).
 */
class VerifyEvmFeeUseCase @Inject constructor(private val evmApiFactory: EvmApiFactory) {

    suspend operator fun invoke(coin: Coin, blockChainSpecific: BlockChainSpecific) {
        if (coin.chain.standard != TokenStandard.EVM) return
        val eth = blockChainSpecific as? BlockChainSpecific.Ethereum ?: return

        require(eth.gasLimit <= MAX_GAS_LIMIT) {
            "Gas limit ${eth.gasLimit} exceeds the maximum ${MAX_GAS_LIMIT}"
        }

        val reference = evmApiFactory.createEvmApi(coin.chain).getGasPrice()
        // A zero/absent reference says nothing, so fall back to the floor rather than reject every
        // fee as "infinitely over a zero reference".
        val ceiling = reference.coerceAtLeast(MIN_REFERENCE_GAS_PRICE_WEI) * MAX_GAS_PRICE_MULTIPLE
        Timber.d(
            "EVM fee check %s: maxFeePerGas=%s priorityFee=%s reference=%s ceiling=%s",
            coin.chain.raw,
            eth.maxFeePerGasWei,
            eth.priorityFeeWei,
            reference,
            ceiling,
        )
        require(eth.maxFeePerGasWei <= ceiling) {
            "Gas price ${eth.maxFeePerGasWei} exceeds $MAX_GAS_PRICE_MULTIPLE× the network rate"
        }
        require(eth.priorityFeeWei <= ceiling) {
            "Priority fee ${eth.priorityFeeWei} exceeds $MAX_GAS_PRICE_MULTIPLE× the network rate"
        }
    }

    private companion object {
        /** No legitimate single transaction spends more than a whole Ethereum block's gas (~30M). */
        val MAX_GAS_LIMIT: BigInteger = BigInteger.valueOf(60_000_000L)

        /**
         * How far over the live `eth_gasPrice` a signed fee may sit. A user speeding a transaction up
         * sets a small multiple; the live reference already tracks congestion, so 50× is headroom no
         * honest fee reaches and a ×1000 inflation never clears.
         */
        val MAX_GAS_PRICE_MULTIPLE: BigInteger = BigInteger.valueOf(50L)

        /** 1 Gwei — floors the reference so a momentarily tiny `eth_gasPrice` can't shrink the ceiling to nothing. */
        val MIN_REFERENCE_GAS_PRICE_WEI: BigInteger = BigInteger.valueOf(1_000_000_000L)
    }
}
