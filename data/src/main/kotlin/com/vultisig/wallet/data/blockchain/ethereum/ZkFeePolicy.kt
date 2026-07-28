package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.models.ZkGasFee
import java.math.BigInteger

/**
 * zkSync's fee pair arrives verbatim from `zks_estimateFee` instead of being derived locally, so it
 * can carry a priority fee above the max fee. That pair is invalid under EIP-1559 (geth rejects it
 * as `ErrTipAboveFeeCap`) and WalletCore performs no such validation while encoding, so the
 * rejection would only surface at broadcast — once the MPC ceremony has already completed and with
 * no automatic retry.
 */
internal fun ZkGasFee.clampedPriorityFee(): BigInteger =
    maxPriorityFeePerGas.coerceAtMost(maxFeePerGas)
