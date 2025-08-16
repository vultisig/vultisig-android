package com.vultisig.wallet.ui.navigation

import android.annotation.SuppressLint
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import timber.log.Timber

internal fun NavController.route(route: String, opts: NavigationOptions? = null) {
    Timber.d("route($route, $opts)")

    if (route == Destination.Back.route) {
        popBackStack()
    } else {
        try {
            navigate(route) {
                buildOptions(
                    this,
                    opts
                )
            }
        } catch (e: Exception) {
            Timber.e(
                e,
                "Navigation failed for route: $route"
            )
            error("Navigation failed for route: $route. the exception is $e")
        }
    }
}

internal fun NavController.route(route: NavigateAction<Any>) {
    Timber.d("route($route)")

    val (dst, opts) = route

    try {
        navigate(dst) {
            buildOptions(
                this,
                opts
            )
        }
    } catch (e: Exception) {
        Timber.e(
            e,
            "Navigation failed for route: $dst"
        )
        error("Navigation failed for route: $route. the exception is $e")
    }
}

@SuppressLint("RestrictedApi")
private fun NavController.buildOptions(
    builder: NavOptionsBuilder,
    opts: NavigationOptions?
) {
    with(builder) {
        launchSingleTop = true
        if (opts != null) {
            if (opts.popUpTo != null) {
                popUpTo(opts.popUpTo) {
                    inclusive = opts.inclusive
                }
            }
            if (opts.popUpToRoute != null) {
                popUpTo(klass = opts.popUpToRoute) {
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