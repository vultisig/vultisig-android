package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/**
 * Runs the real teardown a nav pop performs — the moment every DeFi detail view-model hands its
 * state to [DeFiPositionsSnapshotCache] — by putting the view-model in a store and clearing it.
 * `onCleared` is protected, so a test cannot call it directly, and stubbing the write instead would
 * test the mock rather than the lifecycle.
 */
internal fun ViewModel.clearForTest() {
    ViewModelStore().apply { put("vm", this@clearForTest) }.clear()
}
