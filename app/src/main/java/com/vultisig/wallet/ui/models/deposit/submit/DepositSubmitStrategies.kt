package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.ui.models.deposit.DepositOption

/**
 * Registry mapping each [DepositOption] to the [DepositSubmitStrategy] that knows how to build its
 * [com.vultisig.wallet.data.models.DepositTransaction].
 *
 * Constructed once per `DepositFormViewModel`. During the Phase 0+1 refactor only Bond/Unbond/Leave
 * are real strategy classes — the remaining options are lambdas that delegate to the existing
 * `createXxxTransaction` methods on the view-model until later phases extract them.
 */
internal typealias DepositSubmitStrategies = Map<DepositOption, DepositSubmitStrategy>
