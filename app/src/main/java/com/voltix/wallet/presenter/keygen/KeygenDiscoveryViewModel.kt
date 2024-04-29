package com.voltix.wallet.presenter.keygen

import androidx.lifecycle.ViewModel
import com.voltix.wallet.common.VoltixRelay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class KeygenDiscoveryViewModel @Inject constructor( private val voltixRelay: VoltixRelay): ViewModel() {

}