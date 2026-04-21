package com.vultisig.wallet.ui.models.send.observers

import com.vultisig.wallet.ui.models.send.SendFormViewModel

/** Manages all init-time flow collectors for [SendFormViewModel]. */
internal class FlowCollectorManager(private val viewModel: SendFormViewModel) {
    /** Starts all flow collectors. Call once from [SendFormViewModel]'s init block. */
    fun start() {
        with(viewModel) {
            loadSelectedCurrency()
            collectSelectedAccount()
            collectAmountChanges()
            calculateGasFees()
            calculateGasTokenBalance()
            collectEstimatedFee()
            collectPlanFee()
            calculateSpecific()
            collectAdvanceGasUi()
            collectAmountChecks()
            loadVaultName()
            loadGasSettings()
            collectDstAddress()
            collectAddress()
            collectMaxAmount()
        }
    }
}
