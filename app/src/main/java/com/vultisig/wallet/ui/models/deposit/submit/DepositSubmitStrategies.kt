package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.ui.models.deposit.DepositOption

/**
 * Registry mapping each [DepositOption] to the [DepositSubmitStrategy] that knows how to build its
 * [com.vultisig.wallet.data.models.DepositTransaction].
 *
 * Constructed once per `DepositFormViewModel`. Options are progressively migrated to real strategy
 * classes (Bond/Unbond/Leave/Stake/Unstake and counting); the remaining options are still lambdas
 * that delegate to the existing `createXxxTransaction` methods on the view-model until later phases
 * extract them.
 */
internal typealias DepositSubmitStrategies = Map<DepositOption, DepositSubmitStrategy>
