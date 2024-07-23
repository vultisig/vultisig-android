package com.vultisig.wallet.ui.navigation

import androidx.navigation.NavController

internal fun NavController.route(route: String, opts: NavigationOptions? = null) {
    if (route == Destination.Back.route) {
        popBackStack()
    } else {
        navigate(route) {
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