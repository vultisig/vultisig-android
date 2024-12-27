package com.vultisig.wallet.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber
import javax.inject.Inject

internal interface Navigator<Dest> {

    val destination: Flow<NavigateAction<Dest>>

    val route: Flow<Any>

    suspend fun route(route: Any)

    suspend fun navigate(destination: Dest)

    suspend fun navigate(dst: Dest, opts: NavigationOptions)

}

internal data class NavigateAction<Dst>(
    val dst: Dst,
    val opts: NavigationOptions? = null,
)

internal data class NavigationOptions(
    val popUpTo: String? = null,
    val inclusive: Boolean = false,
    val clearBackStack: Boolean = false,
)

internal class NavigatorImpl<Dst> @Inject constructor() : Navigator<Dst> {

    override val destination = MutableSharedFlow<NavigateAction<Dst>>()

    override val route = MutableSharedFlow<Any>()

    override suspend fun route(route: Any) {
        this.route.emit(route)
    }

    override suspend fun navigate(destination: Dst) {
        Timber.d("navigate($destination)")
        this.destination.emit(NavigateAction(destination))
    }

    override suspend fun navigate(dst: Dst, opts: NavigationOptions) {
        Timber.d("navigate($destination, $opts)")
        this.destination.emit(NavigateAction(dst, opts))
    }

}
