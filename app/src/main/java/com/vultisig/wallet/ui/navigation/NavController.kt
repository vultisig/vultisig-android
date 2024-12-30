package com.vultisig.wallet.ui.navigation

import androidx.navigation.NavController
import timber.log.Timber

internal fun NavController.route(route: String, opts: NavigationOptions? = null) {
    Timber.d("route($route, $opts)")

    if (route == Destination.Back.route) {
        popBackStack()
    } else {
        navigate(route) {
            launchSingleTop=true
            if (opts != null) {
                if (opts.popUpTo != null) {
                    popUpTo(opts.popUpTo) {
                        inclusive = opts.inclusive
                    }
                }
                if (opts.clearBackStack) {
                    popUpTo(graph.id) {
                        inclusive = true
                    }
                }
            }
        }
    }
}

internal fun NavController.route(route: Any) {
    Timber.d("route($route)")

    navigate(route)
}