package com.vultisig.wallet.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

internal interface Navigator<Dest> {

    val destination: Flow<Dest>

    suspend fun navigate(destination: Dest)

}

internal class NavigatorImpl<Dest> @Inject constructor() : Navigator<Dest> {

    override val destination = MutableSharedFlow<Dest>()

    override suspend fun navigate(destination: Dest) {
        this.destination.emit(destination)
    }

}
